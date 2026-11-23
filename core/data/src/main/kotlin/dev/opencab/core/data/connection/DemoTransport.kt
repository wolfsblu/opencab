package dev.opencab.core.data.connection

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A hardware-free WiThrottle session.
 *
 * It runs entirely in-process: there is no socket and no network access. The session stays alive
 * until the caller cancels it, so the rest of the app sees the same connection lifecycle as a real
 * layout. Later steps can extend this with scripted speed and function responses; the connection
 * screen only needs the handshake and an honest, clearly-labelled link.
 */
@Singleton
class DemoTransport @Inject constructor() {
    /**
     * Pretends to complete the TCP connect and greeting, then suspends forever.
     *
     * [onReady] runs once the simulated server has accepted the session. Cancelling the calling
     * coroutine ends the session cleanly.
     */
    suspend fun run(onReady: () -> Unit) {
        delay(CONNECT_DELAY_MILLIS)
        onReady()
        awaitCancellation()
    }

    companion object {
        /** Long enough to see the connecting state, short enough to feel instant on a demo phone. */
        const val CONNECT_DELAY_MILLIS: Long = 450L
    }
}
