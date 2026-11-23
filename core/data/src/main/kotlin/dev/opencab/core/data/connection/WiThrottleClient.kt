package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ServerEndpoint
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import dev.opencab.core.protocol.codec.WiThrottleDecoder
import dev.opencab.core.protocol.codec.WiThrottleEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * WiThrottle over a coroutine socket.
 *
 * Lifecycle:
 * 1. `Disconnected → Connecting` and the TCP connect with a timeout.
 * 2. Send `HU`/`N`, then read until `VN` (or a terminal greeting failure).
 * 3. `Connected`, send `*+`, start the writer, heartbeat, and liveness watchdog.
 * 4. On an established link dropping, `Reconnecting` with capped, jittered backoff.
 * 5. An unrecoverable first failure, or an unrecoverable reconnect failure, becomes `Failed`.
 *
 * The read loop feeds bytes to the incremental [WiThrottleDecoder], so a partial frame at EOF is
 * discarded rather than decoded as if it were complete. The socket is closed from [close], from
 * cancellation, and from the heartbeat watchdog, which is what unblocks a pending read.
 */
class WiThrottleClient(
    private val config: DccClientConfig = DccClientConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val networkBinder: NetworkBinder = NetworkBinder.Direct,
) : DccClient {
    private val lock = Any()
    private val reconnectPolicy = ReconnectPolicy.from(config)
    private val mutableState = MutableStateFlow<DccConnectionState>(DccConnectionState.Disconnected)
    override val state: StateFlow<DccConnectionState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<WiThrottleEvent>(
        replay = EVENT_REPLAY,
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<WiThrottleEvent> = mutableEvents.asSharedFlow()

    @Volatile
    private var session: Session? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun connect(endpoint: ServerEndpoint) {
        awaitPreviousSession()
        mutableEvents.resetReplayCache()
        val newSession = Session()
        synchronized(lock) {
            check(session == null) { "WiThrottleClient is already connected" }
            session = newSession
        }

        try {
            withContext(dispatcher) {
                coroutineScope {
                    newSession.job = currentCoroutineContext()[Job]
                    if (newSession.closing) throw CancellationException()
                    runSession(endpoint, newSession)
                }
            }
        } finally {
            val wasClosed = newSession.closing
            newSession.closing = true
            newSession.closeSocket()
            newSession.commands.close()
            synchronized(lock) {
                if (session === newSession) session = null
            }
            if (wasClosed || mutableState.value !is DccConnectionState.Failed) {
                mutableState.value = DccConnectionState.Disconnected
            }
            newSession.finished.complete(Unit)
        }
    }

    override suspend fun send(command: WiThrottleCommand): Boolean {
        val current = session ?: return false
        if (!current.connected || current.closing) return false
        return try {
            current.commands.send(command)
            true
        } catch (_: ClosedSendChannelException) {
            false
        }
    }

    override fun trySend(command: WiThrottleCommand): Boolean {
        val current = session ?: return false
        if (!current.connected || current.closing) return false
        return current.commands.trySend(command).isSuccess
    }

    override fun close() {
        val current = synchronized(lock) { session }
        if (current == null) {
            mutableState.value = DccConnectionState.Disconnected
            return
        }
        current.closing = true
        current.closeSocket()
        current.commands.close()
        current.job?.cancel()
        mutableState.value = DccConnectionState.Disconnected
    }

    private suspend fun awaitPreviousSession() {
        val previous = synchronized(lock) { session } ?: return
        close()
        previous.finished.await()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runSession(
        endpoint: ServerEndpoint,
        session: Session,
    ) {
        var everConnected = false
        var attempt = 0
        var lastError: ConnectionError? = null

        while (currentCoroutineContext().isActive && !session.closing) {
            if (everConnected) {
                attempt += 1
                val retryInMillis = reconnectPolicy.delayMillis(attempt)
                mutableState.value = DccConnectionState.Reconnecting(
                    endpoint = endpoint,
                    attempt = attempt,
                    lastError = lastError ?: ConnectionError.ServerClosed,
                    retryInMillis = retryInMillis,
                )
                delay(retryInMillis)
            } else {
                mutableState.value = DccConnectionState.Connecting(endpoint)
            }

            try {
                if (everConnected) session.commands.drain()
                val connection = openConnection(endpoint, session)
                everConnected = true
                attempt = 0
                session.connected = true
                mutableState.value = DccConnectionState.Connected(
                    endpoint = endpoint,
                    serverVersion = connection.serverVersion,
                    heartbeatIntervalMillis = connection.heartbeatIntervalMillis,
                )
                try {
                    pump(connection, session)
                    throw SessionClosedException()
                } finally {
                    session.connected = false
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (session.closing) return
                val error = throwable.toConnectionError()
                lastError = error
                if (!everConnected || !error.isRetryable()) {
                    mutableState.value = DccConnectionState.Failed(endpoint, error)
                    return
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun openConnection(
        endpoint: ServerEndpoint,
        session: Session,
    ): Connection {
        val socket = Socket()
        session.socket = socket
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            val host = endpoint.host.removeSurrounding("[", "]")
            val address = networkBinder.bind(socket, host, endpoint.port)
                ?: InetSocketAddress(host, endpoint.port)
            socket.connect(address, config.connectTimeoutMillis)
            socket.soTimeout = config.readTimeoutMillis

            val connection = Connection(
                endpoint = endpoint,
                input = socket.getInputStream(),
                output = socket.getOutputStream(),
                session = session,
            )
            connection.write(WiThrottleCommand.SetHardwareId(CLIENT_ID))
            connection.write(WiThrottleCommand.SetDeviceName(CLIENT_NAME))
            awaitGreeting(connection)
            connection.write(WiThrottleCommand.EnableHeartbeatMonitoring)
            return connection
        } catch (throwable: Throwable) {
            session.closeSocket()
            throw throwable
        }
    }

    @Suppress("NestedBlockDepth", "ThrowsCount")
    private suspend fun awaitGreeting(connection: Connection) {
        val deadline = System.nanoTime() + config.greetingTimeoutMillis * NANOS_PER_MILLI
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var version: ProtocolVersion? = null

        while (System.nanoTime() < deadline) {
            if (!currentCoroutineContext().isActive || connection.session.closing) {
                throw CancellationException()
            }
            val count = read(connection, buffer)
            if (count < 0) throw SessionClosedException()
            if (count == 0) continue
            connection.noteInbound()
            for (event in connection.decoder.feed(buffer.copyOf(count))) {
                when (event) {
                    is WiThrottleEvent.Version -> {
                        if (event.version < config.minimumProtocolVersion) {
                            throw UnsupportedVersionException(event.version.raw)
                        }
                        version = event.version
                        connection.serverVersion = event.version
                        mutableEvents.emit(event)
                    }

                    is WiThrottleEvent.HeartbeatInterval -> {
                        connection.updateHeartbeat(event.seconds)
                        mutableEvents.emit(event)
                    }

                    is WiThrottleEvent.Alert -> {
                        if (version == null) throw ServerRejectedException()
                        mutableEvents.emit(event)
                    }

                    is WiThrottleEvent.Unknown -> {
                        if (version == null) throw ProtocolMismatchException()
                        mutableEvents.emit(event)
                    }

                    else -> mutableEvents.emit(event)
                }
            }
            if (version != null) return
        }
        throw GreetingTimeoutException()
    }

    private suspend fun pump(
        connection: Connection,
        session: Session,
    ) = coroutineScope {
        val writer = launch {
            while (isActive) {
                val command = session.commands.receiveCatching().getOrNull() ?: break
                connection.write(command)
            }
        }
        val heartbeat = launch {
            heartbeatLoop(connection, session)
        }
        try {
            val buffer = ByteArray(READ_BUFFER_BYTES)
            while (isActive && !session.closing) {
                val count = read(connection, buffer)
                if (count < 0) throw connection.terminalError ?: SessionClosedException()
                if (count == 0) continue
                connection.noteInbound()
                for (event in connection.decoder.feed(buffer.copyOf(count))) {
                    when (event) {
                        is WiThrottleEvent.HeartbeatInterval -> {
                            connection.updateHeartbeat(event.seconds)
                            mutableState.value = DccConnectionState.Connected(
                                endpoint = connection.endpoint,
                                serverVersion = connection.serverVersion,
                                heartbeatIntervalMillis = connection.heartbeatIntervalMillis,
                            )
                            mutableEvents.emit(event)
                        }

                        is WiThrottleEvent.Alert -> mutableEvents.emit(event)

                        else -> mutableEvents.emit(event)
                    }
                }
            }
        } finally {
            heartbeat.cancel()
            writer.cancel()
        }
    }

    /**
     * Sends a heartbeat on the negotiated cadence and probes a quiet server with `N`.
     *
     * JMRI does not send unsolicited traffic, so silence alone is not proof of a dead link. Every
     * `2 ×` heartbeat interval the watchdog sends `N<deviceName>`, which makes JMRI reply with
     * `*<seconds>`. If that reply does not arrive within [DccClientConfig.heartbeatProbeTimeoutMillis],
     * the link is dead and the socket is closed so the read loop unblocks.
     */
    @Suppress(
        "LoopWithTooManyJumpStatements",
        "NestedBlockDepth",
        "ThrowsCount",
        "TooGenericExceptionCaught",
    )
    private suspend fun heartbeatLoop(
        connection: Connection,
        session: Session,
    ) {
        try {
            while (currentCoroutineContext().isActive && !session.closing) {
                val interval = connection.effectiveHeartbeatMillis(config.heartbeatIntervalMillis)
                if (interval <= 0) {
                    delay(HEARTBEAT_DISABLED_TICK_MILLIS)
                    continue
                }

                delay(WATCHDOG_TICK_MILLIS)
                val now = System.nanoTime()
                if (now - connection.lastOutboundNanos >= heartbeatSendIntervalNanos(interval)) {
                    connection.write(WiThrottleCommand.Heartbeat)
                }

                val silenceLimit = heartbeatSilenceNanos(interval)
                if (connection.silenceNanos(now) < silenceLimit) {
                    connection.probePending = false
                    continue
                }

                if (connection.probePending) {
                    if (connection.silenceNanos(now) >=
                        silenceLimit + config.heartbeatProbeTimeoutMillis * NANOS_PER_MILLI
                    ) {
                        throw HeartbeatTimeoutException()
                    }
                } else {
                    connection.probePending = true
                    connection.write(WiThrottleCommand.SetDeviceName(CLIENT_NAME))
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            connection.terminalError = throwable
            connection.closeSocket()
            throw throwable
        }
    }

    private suspend fun read(
        connection: Connection,
        buffer: ByteArray,
    ): Int = try {
        connection.input.read(buffer)
    } catch (_: SocketTimeoutException) {
        if (connection.session.closing) throw CancellationException()
        connection.terminalError?.let { throw it }
        0
    } catch (socket: SocketException) {
        if (connection.session.closing) throw CancellationException()
        throw connection.terminalError ?: socket
    }

    private fun heartbeatSendIntervalNanos(intervalMillis: Long): Long {
        val sendNanos = (intervalMillis * HEARTBEAT_SEND_FRACTION * NANOS_PER_MILLI).toLong()
        return sendNanos.coerceAtLeast(WATCHDOG_TICK_MILLIS * NANOS_PER_MILLI)
    }

    private fun heartbeatSilenceNanos(intervalMillis: Long): Long {
        val multiplier = config.heartbeatSilenceMultiplier.coerceAtLeast(1.0)
        return (intervalMillis * multiplier * NANOS_PER_MILLI).toLong()
    }

    private class Session {
        @Volatile
        var job: Job? = null

        @Volatile
        var closing: Boolean = false

        @Volatile
        var connected: Boolean = false

        val commands = Channel<WiThrottleCommand>(COMMAND_BUFFER)

        @Volatile
        var socket: Socket? = null

        val finished = CompletableDeferred<Unit>()

        fun closeSocket() {
            socket?.closeQuietly()
            socket = null
        }
    }

    private class Connection(
        val endpoint: ServerEndpoint,
        val input: InputStream,
        val output: OutputStream,
        val session: Session,
    ) {
        val decoder = WiThrottleDecoder()
        private val writeMutex = Mutex()

        @Volatile
        var serverVersion: ProtocolVersion = ProtocolVersion.V2_0

        @Volatile
        var heartbeatIntervalMillis: Long? = null

        @Volatile
        var lastInboundNanos: Long = System.nanoTime()

        @Volatile
        var lastOutboundNanos: Long = System.nanoTime()

        @Volatile
        var probePending: Boolean = false

        /** Set before the watchdog closes the socket so the read loop reports the real cause. */
        @Volatile
        var terminalError: Throwable? = null

        suspend fun write(command: WiThrottleCommand) {
            writeMutex.withLock {
                val bytes = (WiThrottleEncoder.encode(command) + "\n").toByteArray(Charsets.UTF_8)
                output.write(bytes)
                output.flush()
                lastOutboundNanos = System.nanoTime()
            }
        }

        fun noteInbound() {
            lastInboundNanos = System.nanoTime()
            probePending = false
        }

        fun silenceNanos(now: Long): Long = (now - lastInboundNanos).coerceAtLeast(0)

        fun effectiveHeartbeatMillis(fallback: Long): Long = heartbeatIntervalMillis ?: fallback

        fun updateHeartbeat(seconds: Int) {
            heartbeatIntervalMillis = seconds * MILLIS_PER_SECOND
        }

        fun closeSocket() {
            session.closeSocket()
        }
    }

    private companion object {
        const val CLIENT_ID: String = "opencab-android"
        const val CLIENT_NAME: String = "OpenCab"
        const val READ_BUFFER_BYTES: Int = 4_096
        const val COMMAND_BUFFER: Int = 64
        const val EVENT_REPLAY: Int = 16
        const val EVENT_BUFFER: Int = 256
        const val NANOS_PER_MILLI: Long = 1_000_000
        const val MILLIS_PER_SECOND: Long = 1_000
        const val WATCHDOG_TICK_MILLIS: Long = 100
        const val HEARTBEAT_DISABLED_TICK_MILLIS: Long = 1_000
        const val HEARTBEAT_SEND_FRACTION: Double = 0.5
    }
}

private fun Socket.closeQuietly() {
    runCatching { close() }
}

private fun Channel<WiThrottleCommand>.drain() {
    while (tryReceive().isSuccess) {
        // Discard movement commands from the previous link. The throttle engine re-acquires.
    }
}

internal class GreetingTimeoutException : IOException("WiThrottle greeting timed out")

internal class HeartbeatTimeoutException : IOException("WiThrottle heartbeat timed out")

internal class SessionClosedException : IOException("WiThrottle session closed")

internal class ServerRejectedException : IOException("WiThrottle server rejected the session")

internal class ProtocolMismatchException : IOException("Unrecognised WiThrottle handshake")

internal class UnsupportedVersionException(
    val serverVersion: String,
) : IOException("Unsupported WiThrottle version: $serverVersion")

/**
 * Maps transport failures to the user-safe taxonomy. Exception messages are intentionally never
 * copied into the result.
 */
internal fun Throwable.toConnectionError(): ConnectionError = when (this) {
    is UnsupportedVersionException -> ConnectionError.UnsupportedVersion(found = serverVersion)

    is ProtocolMismatchException -> ConnectionError.ProtocolMismatch

    is GreetingTimeoutException, is HeartbeatTimeoutException -> ConnectionError.TimedOut

    is SessionClosedException -> ConnectionError.ServerClosed

    is ServerRejectedException -> ConnectionError.NotWiThrottle

    is SocketTimeoutException -> ConnectionError.TimedOut

    is ConnectException -> ConnectionError.ConnectionRefused

    is NoRouteToHostException -> ConnectionError.Unreachable

    is UnknownHostException -> ConnectionError.HostNotFound

    is SecurityException -> ConnectionError.LocalNetworkPermissionDenied

    is SocketException ->
        when {
            isLocalNetworkPermissionDenied() -> ConnectionError.LocalNetworkPermissionDenied
            isNetworkUnreachable() -> ConnectionError.Unreachable
            else -> ConnectionError.ServerClosed
        }

    is IOException -> ConnectionError.Unknown

    else -> ConnectionError.Unknown
}

/**
 * Android reports a local-network block as `EPERM` on the socket, which surfaces as a
 * [SocketException] rather than a [SecurityException]. The text is inspected only to pick a
 * typed error; it is never shown.
 */
private fun Throwable.isLocalNetworkPermissionDenied(): Boolean = generateSequence(this) { it.cause }
    .any { cause ->
        cause is SecurityException ||
            (cause is SocketException && cause.message.hasAny("permission denied", "operation not permitted"))
    }

private fun Throwable.isNetworkUnreachable(): Boolean = generateSequence(this) { it.cause }
    .any { cause -> cause is SocketException && cause.message.hasAny("network is unreachable") }

private fun String?.hasAny(vararg needles: String): Boolean {
    if (this == null) return false
    return needles.any { contains(it, ignoreCase = true) }
}

internal fun ConnectionError.isRetryable(): Boolean = when (this) {
    ConnectionError.LocalNetworkPermissionDenied,
    ConnectionError.NotWiThrottle,
    ConnectionError.ProtocolMismatch,
    is ConnectionError.UnsupportedVersion,
    -> false

    else -> true
}
