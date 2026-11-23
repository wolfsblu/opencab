package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ServerEndpoint
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import dev.opencab.core.protocol.codec.WiThrottleDecoder
import dev.opencab.core.protocol.codec.WiThrottleEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * One live WiThrottle socket session.
 *
 * The class owns the socket, the client greeting, the version gate, and a lightweight heartbeat.
 * It exposes no protocol type outside `:core:data`; the repository turns its success and failure
 * into [dev.opencab.core.model.ConnectionState].
 *
 * Cancellation closes the socket from the caller side, which unblocks a pending read.
 */
internal class WiThrottleSession(
    private val endpoint: ServerEndpoint,
) {
    @Volatile
    private var socket: Socket? = null

    /** Closes the socket, if any. Safe to call from another coroutine. */
    fun close() {
        runCatching { socket?.close() }
    }

    /**
     * Connects, performs the greeting and version gate, calls [onReady], then pumps the link until
     * it closes or is cancelled. Returns only by throwing.
     */
    suspend fun run(onReady: () -> Unit) {
        withContext(Dispatchers.IO) {
            val sessionSocket = Socket()
            socket = sessionSocket
            try {
                sessionSocket.tcpNoDelay = true
                sessionSocket.connect(
                    InetSocketAddress(endpoint.host.removeSurrounding("[", "]"), endpoint.port),
                    CONNECT_TIMEOUT_MILLIS,
                )
                sessionSocket.soTimeout = READ_TIMEOUT_MILLIS

                val writer = BufferedWriter(
                    OutputStreamWriter(sessionSocket.getOutputStream(), Charsets.UTF_8),
                )
                val reader = BufferedReader(
                    InputStreamReader(sessionSocket.getInputStream(), Charsets.UTF_8),
                )
                val decoder = WiThrottleDecoder()
                val send = { command: WiThrottleCommand ->
                    writer.write(WiThrottleEncoder.encode(command))
                    writer.write("\n")
                    writer.flush()
                }

                send(WiThrottleCommand.SetHardwareId(CLIENT_ID))
                send(WiThrottleCommand.SetDeviceName(CLIENT_NAME))
                awaitGreeting(decoder, reader)
                send(WiThrottleCommand.EnableHeartbeatMonitoring)

                onReady()
                pump(decoder, reader, send)
            } finally {
                runCatching { sessionSocket.close() }
                socket = null
            }
        }
    }

    @Suppress("ThrowsCount")
    private suspend fun awaitGreeting(
        decoder: WiThrottleDecoder,
        reader: BufferedReader,
    ) {
        val deadline = System.nanoTime() + GREETING_TIMEOUT_NANOS
        while (System.nanoTime() < deadline) {
            ensureActive()
            var timedOut = false
            val line = try {
                reader.readLine()
            } catch (_: SocketTimeoutException) {
                timedOut = true
                null
            }
            if (timedOut) continue
            if (line == null) throw SessionClosedException()
            when (val event = decoder.decode(line)) {
                is WiThrottleEvent.Version -> {
                    if (event.version < ProtocolVersion.V2_0) {
                        throw UnsupportedVersionException(event.version.raw)
                    }
                    return
                }

                is WiThrottleEvent.Alert -> throw ServerRejectedException()

                else -> Unit
            }
        }
        throw GreetingTimeoutException()
    }

    private suspend fun pump(
        decoder: WiThrottleDecoder,
        reader: BufferedReader,
        send: (WiThrottleCommand) -> Unit,
    ) {
        var lastHeartbeat = System.nanoTime()
        while (true) {
            ensureActive()
            val now = System.nanoTime()
            if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_NANOS) {
                send(WiThrottleCommand.Heartbeat)
                lastHeartbeat = now
            }
            var timedOut = false
            val line = try {
                reader.readLine()
            } catch (_: SocketTimeoutException) {
                timedOut = true
                null
            }
            if (timedOut) continue
            if (line == null) throw SessionClosedException()
            decoder.decode(line)
        }
    }

    private suspend fun ensureActive() {
        if (!currentCoroutineContext().isActive) throw CancellationException()
    }

    private companion object {
        const val CLIENT_ID: String = "opencab-android"
        const val CLIENT_NAME: String = "OpenCab"
        const val CONNECT_TIMEOUT_MILLIS: Int = 4_000
        const val READ_TIMEOUT_MILLIS: Int = 1_000
        const val GREETING_TIMEOUT_NANOS: Long = 5_000_000_000L
        const val HEARTBEAT_INTERVAL_NANOS: Long = 5_000_000_000L
    }
}

internal class GreetingTimeoutException : IOException("WiThrottle greeting timed out")

internal class SessionClosedException : IOException("WiThrottle session closed")

internal class ServerRejectedException : IOException("WiThrottle server rejected the session")

internal class UnsupportedVersionException(
    val serverVersion: String,
) : IOException("Unsupported WiThrottle version: $serverVersion")

/**
 * Maps transport failures to the user-safe taxonomy. Exception messages are intentionally never
 * copied into the result.
 */
internal fun Throwable.toConnectionError(): ConnectionError = when (this) {
    is UnsupportedVersionException -> ConnectionError.UnsupportedVersion(found = serverVersion)
    is GreetingTimeoutException -> ConnectionError.TimedOut
    is SessionClosedException -> ConnectionError.ServerClosed
    is ServerRejectedException -> ConnectionError.NotWiThrottle
    is SocketTimeoutException -> ConnectionError.TimedOut
    is ConnectException -> ConnectionError.ConnectionRefused
    is NoRouteToHostException -> ConnectionError.Unreachable
    is UnknownHostException -> ConnectionError.HostNotFound
    is SecurityException -> ConnectionError.LocalNetworkPermissionDenied
    is SocketException -> ConnectionError.ServerClosed
    is IOException -> ConnectionError.Unknown
    else -> ConnectionError.Unknown
}
