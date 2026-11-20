package dev.opencab.core.model

/**
 * High-level state of the connection between the app and a command station.
 *
 * Transport and protocol handling are intentionally not implemented in this milestone.
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    data class Connected(
        val endpoint: String,
    ) : ConnectionState
}
