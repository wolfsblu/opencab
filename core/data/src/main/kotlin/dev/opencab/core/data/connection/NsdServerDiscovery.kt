package dev.opencab.core.data.connection

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.DiscoveredServer
import dev.opencab.core.model.ServerEndpoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `NsdManager` implementation of [ServerDiscovery] for `_withrottle._tcp`.
 *
 * Discovery is deliberately best-effort: a missing service, a resolution failure, or an empty
 * browse is normal on many networks. Results are de-duplicated by host:port and emitted while the
 * scan runs; the terminal snapshot always arrives unless the collector cancels.
 */
@Singleton
class NsdServerDiscovery @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ServerDiscovery {
    override fun scan(timeoutMillis: Long): Flow<DiscoverySnapshot> = channelFlow {
        if (!LocalNetworkAccess.isGranted(context)) {
            send(
                DiscoverySnapshot(
                    finished = true,
                    error = ConnectionError.LocalNetworkPermissionDenied,
                ),
            )
            return@channelFlow
        }

        val nsdManager = context.getSystemService(NsdManager::class.java)
        if (nsdManager == null) {
            send(
                DiscoverySnapshot(
                    finished = true,
                    error = ConnectionError.NotWiThrottle,
                ),
            )
            return@channelFlow
        }

        val found = linkedMapOf<String, DiscoveredServer>()
        val serviceEndpoints = mutableMapOf<String, String>()
        val stopped = AtomicBoolean(false)
        val failure = CompletableDeferred<ConnectionError>()
        val discoveryListener = discoveryListener(
            nsdManager = nsdManager,
            found = found,
            serviceEndpoints = serviceEndpoints,
            stopped = stopped,
            failure = failure,
        )

        try {
            nsdManager.discoverServices(
                ServerDiscovery.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener,
            )
            emitSnapshot(found, finished = false)
            val error = withTimeoutOrNull(timeoutMillis) { failure.await() }
            emitSnapshot(found, finished = true, error = error)
        } catch (security: SecurityException) {
            Log.w(TAG, "discoverServices denied", security)
            send(
                DiscoverySnapshot(
                    finished = true,
                    error = ConnectionError.LocalNetworkPermissionDenied,
                ),
            )
        } catch (illegalArgument: IllegalArgumentException) {
            Log.w(TAG, "discoverServices rejected", illegalArgument)
            send(
                DiscoverySnapshot(
                    finished = true,
                    error = ConnectionError.Unknown,
                ),
            )
        } finally {
            stopDiscovery(nsdManager, discoveryListener, stopped)
        }
    }

    private fun ProducerScope<DiscoverySnapshot>.discoveryListener(
        nsdManager: NsdManager,
        found: MutableMap<String, DiscoveredServer>,
        serviceEndpoints: MutableMap<String, String>,
        stopped: AtomicBoolean,
        failure: CompletableDeferred<ConnectionError>,
    ): NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            resolve(nsdManager, serviceInfo, stopped) { server ->
                if (stopped.get()) return@resolve
                synchronized(found) {
                    found[server.endpoint.display] = server
                    serviceEndpoints[serviceInfo.serviceName.orEmpty()] = server.endpoint.display
                }
                emitSnapshot(found, finished = false)
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            if (stopped.get()) return
            synchronized(found) {
                val display = serviceEndpoints.remove(serviceInfo.serviceName.orEmpty())
                    ?: serviceInfo.endpointOrNull()?.display
                if (display != null) found.remove(display)
            }
            emitSnapshot(found, finished = false)
        }

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(
            serviceType: String,
            errorCode: Int,
        ) {
            Log.w(TAG, "onStartDiscoveryFailed code=$errorCode")
            stopDiscovery(nsdManager, this, stopped)
            failure.complete(
                if (errorCode == NsdManager.FAILURE_INTERNAL_ERROR) {
                    ConnectionError.LocalNetworkPermissionDenied
                } else {
                    ConnectionError.Unknown
                },
            )
        }

        override fun onStopDiscoveryFailed(
            serviceType: String,
            errorCode: Int,
        ) = Unit
    }

    private fun resolve(
        nsdManager: NsdManager,
        serviceInfo: NsdServiceInfo,
        stopped: AtomicBoolean,
        onResolved: (DiscoveredServer) -> Unit,
    ) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(
                serviceInfo: NsdServiceInfo,
                errorCode: Int,
            ) {
                Log.d(TAG, "onResolveFailed ${serviceInfo.serviceName} code=$errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                if (stopped.get()) return
                val endpoint = serviceInfo.endpointOrNull() ?: return
                val name = serviceInfo.serviceName?.takeIf { it.isNotBlank() } ?: endpoint.host
                onResolved(DiscoveredServer(name = name, endpoint = endpoint))
            }
        }
        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (_: IllegalArgumentException) {
            // A duplicate or already-cancelled service; the browse listener will report another.
        } catch (_: SecurityException) {
            // Permission loss is surfaced by the browse callback, which owns the terminal state.
        }
    }

    private fun ProducerScope<DiscoverySnapshot>.emitSnapshot(
        found: Map<String, DiscoveredServer>,
        finished: Boolean,
        error: ConnectionError? = null,
    ) {
        val snapshot = synchronized(found) {
            DiscoverySnapshot(
                servers = found.values.toList(),
                finished = finished,
                error = error,
            )
        }
        trySend(snapshot)
    }

    private fun stopDiscovery(
        nsdManager: NsdManager,
        listener: NsdManager.DiscoveryListener,
        stopped: AtomicBoolean,
    ) {
        if (!stopped.compareAndSet(false, true)) return
        runCatching { nsdManager.stopServiceDiscovery(listener) }
    }

    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.endpointOrNull(): ServerEndpoint? {
        if (port !in ServerEndpoint.MIN_PORT..ServerEndpoint.MAX_PORT) return null
        val address =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                hostAddresses.firstOrNull()?.hostAddress ?: host?.hostAddress
            } else {
                host?.hostAddress
            } ?: return null
        return runCatching { ServerEndpoint(host = address, port = port) }.getOrNull()
    }

    private companion object {
        const val TAG: String = "OpenCabDiscovery"
    }
}
