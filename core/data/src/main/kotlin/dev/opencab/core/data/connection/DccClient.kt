package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ServerEndpoint
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The link state machine shared by command-station transports.
 *
 * The sequence is `Disconnected → Connecting → Connected`, with `Reconnecting` after an
 * established link drops and `Failed` when a failure cannot be recovered by retrying.
 */
sealed interface DccConnectionState {
    data object Disconnected : DccConnectionState

    data class Connecting(val endpoint: ServerEndpoint) : DccConnectionState

    data class Connected(
        val endpoint: ServerEndpoint,
        val serverVersion: ProtocolVersion,
        val heartbeatIntervalMillis: Long?,
    ) : DccConnectionState

    data class Reconnecting(
        val endpoint: ServerEndpoint,
        val attempt: Int,
        val lastError: ConnectionError,
        val retryInMillis: Long,
    ) : DccConnectionState

    data class Failed(
        val endpoint: ServerEndpoint,
        val error: ConnectionError,
    ) : DccConnectionState
}

/**
 * A command-station transport.
 *
 * [connect] suspends for the lifetime of the link. It owns reconnection for an established link
 * and returns only after [close] or an unrecoverable failure. Callers launch it in a dedicated
 * coroutine and observe [state]; cancelling that coroutine closes the socket.
 *
 * Commands are written on a bounded outbound channel. [send] and [trySend] report `false` while
 * the link is not connected; the throttle engine re-acquires after a reconnect rather than
 * replaying stale movement commands.
 */
interface DccClient {
    val state: StateFlow<DccConnectionState>

    /** Decoded inbound messages, including the greeting and heartbeat interval. */
    val events: Flow<WiThrottleEvent>

    suspend fun connect(endpoint: ServerEndpoint)

    suspend fun send(command: WiThrottleCommand): Boolean

    fun trySend(command: WiThrottleCommand): Boolean

    /** Closes the socket and cancels the session. Safe to call repeatedly and concurrently. */
    fun close()
}

/** Creates a fresh live client for each connection attempt. */
fun interface DccClientFactory {
    fun create(): DccClient
}
