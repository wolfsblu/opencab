package dev.opencab.core.data.throttle

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only throttle view for surfaces outside the drive screen.
 *
 * The roster needs the active address and the layout's roster list, but must not be able to send
 * speed or function commands. Depending on this narrow port keeps that boundary explicit and
 * makes roster view models testable without a socket.
 */
interface ThrottleUiSource {
    val uiState: StateFlow<ThrottleUiState>
}
