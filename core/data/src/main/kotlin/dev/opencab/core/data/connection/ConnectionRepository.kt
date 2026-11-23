package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionState
import dev.opencab.core.model.ConnectionTarget
import kotlinx.coroutines.flow.StateFlow

/** App-scoped owner of the live link. The UI reads [state] and never touches a socket. */
interface ConnectionRepository {
    val state: StateFlow<ConnectionState>

    /**
     * The transport for the active session, or `null` while no socket is open.
     *
     * The throttle engine attaches to this client once and then follows its reconnect state,
     * so a dropped link does not detach the engine. Exposed at the data boundary only; feature
     * composables never see a [DccClient].
     */
    val activeClient: StateFlow<DccClient?>

    fun connect(target: ConnectionTarget)

    /** Cancels an in-flight attempt or ends the active session. */
    fun cancel()

    /** Ends the active session and clears any failure. */
    fun disconnect()
}
