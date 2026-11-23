package dev.opencab.core.data.connection

import dev.opencab.core.model.ConnectionState
import dev.opencab.core.model.ConnectionTarget
import kotlinx.coroutines.flow.StateFlow

/** App-scoped owner of the live link. The UI reads [state] and never touches a socket. */
interface ConnectionRepository {
    val state: StateFlow<ConnectionState>

    fun connect(target: ConnectionTarget)

    /** Cancels an in-flight attempt or ends the active session. */
    fun cancel()

    /** Ends the active session and clears any failure. */
    fun disconnect()
}
