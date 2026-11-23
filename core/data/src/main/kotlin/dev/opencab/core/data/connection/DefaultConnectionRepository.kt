package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionState
import dev.opencab.core.model.ConnectionTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single app-wide link.
 *
 * The client owns the socket-level state machine and reconnect policy; this repository maps that
 * state into the user-facing [ConnectionState] and keeps it alive across navigation and
 * configuration changes.
 */
@Singleton
class DefaultConnectionRepository @Inject constructor(
    private val clientFactory: DccClientFactory,
    ioDispatcher: CoroutineDispatcher,
) : ConnectionRepository {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = mutableState.asStateFlow()

    private var sessionJob: Job? = null

    @Volatile
    private var activeClient: DccClient? = null

    private var generation: Long = 0

    @Suppress("TooGenericExceptionCaught")
    override fun connect(target: ConnectionTarget) {
        generation += 1
        val attemptGeneration = generation
        stopTransport()
        mutableState.value = ConnectionState.Connecting(target)

        sessionJob = scope.launch {
            val client = clientFactory.create()
            activeClient = client
            try {
                runClient(target, client, attemptGeneration)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (attemptGeneration == generation) {
                    mutableState.value = ConnectionState.Failed(
                        target = target,
                        error = throwable.toConnectionError(),
                    )
                }
            } finally {
                client.close()
                if (activeClient === client) activeClient = null
            }
        }
    }

    override fun cancel() {
        generation += 1
        stopTransport()
        mutableState.value = ConnectionState.Disconnected
    }

    override fun disconnect() {
        cancel()
    }

    private suspend fun runClient(
        target: ConnectionTarget,
        client: DccClient,
        attemptGeneration: Long,
    ) {
        coroutineScope {
            val stateJob = launch {
                client.state.collect { dccState ->
                    if (attemptGeneration != generation) return@collect
                    dccState.toConnectionState(target)?.let { mutableState.value = it }
                }
            }
            try {
                client.connect(target.endpoint)
                if (attemptGeneration == generation) {
                    client.state.value.toConnectionState(target)?.let { mutableState.value = it }
                }
            } finally {
                stateJob.cancel()
            }
        }
    }

    private fun stopTransport() {
        sessionJob?.cancel()
        sessionJob = null
        activeClient?.close()
        activeClient = null
    }
}

private fun DccConnectionState.toConnectionState(target: ConnectionTarget): ConnectionState? = when (this) {
    DccConnectionState.Disconnected -> null

    is DccConnectionState.Connecting -> ConnectionState.Connecting(target)

    is DccConnectionState.Connected -> ConnectionState.Connected(target)

    is DccConnectionState.Reconnecting -> ConnectionState.Reconnecting(
        target = target,
        attempt = attempt,
    )

    is DccConnectionState.Failed -> ConnectionState.Failed(target = target, error = error)
}
