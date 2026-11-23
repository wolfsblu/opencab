package dev.opencab.feature.connection

import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ConnectionMode
import dev.opencab.core.model.ConnectionTarget
import dev.opencab.core.model.ServerEndpoint

/** Runtime permission state for local-network discovery. */
enum class PermissionUiState {
    Checking,
    Granted,
    Needed,
    Denied,
}

/** Progress of the mDNS browse, independent of how many servers it found. */
enum class ScanPhase {
    Idle,
    Scanning,
    Complete,
}

/** Where a server row came from. */
enum class ServerSource {
    Discovered,
    Saved,
}

/** Row-level link state, always paired with an icon and a word in the UI. */
enum class ServerStatus {
    Available,
    Connecting,
    Connected,
    Failed,
}

/** One server row, fully resolved for rendering. */
data class ServerCardUiState(
    val id: String,
    val name: String,
    val endpoint: ServerEndpoint,
    val lastConnectedEpochMillis: Long? = null,
    val source: ServerSource,
    val status: ServerStatus = ServerStatus.Available,
) {
    val hostPort: String
        get() = endpoint.display
}

/** The active or in-flight link. */
data class LinkUiState(
    val target: ConnectionTarget,
    val phase: LinkPhase,
    val attempt: Int = 0,
)

enum class LinkPhase {
    Connecting,
    Connected,
    Reconnecting,
}

/** The latest failure, retained until the operator retries or dismisses it. */
data class FailureUiState(
    val target: ConnectionTarget,
    val error: ConnectionError,
)

/** Inline validation result for one manual-entry field. */
enum class ManualFieldError {
    Required,
    InvalidHost,
    InvalidPort,
}

/** The manual-entry sheet's immutable editing state. */
data class ManualEntryUiState(
    val editingId: String? = null,
    val name: String = "",
    val host: String = "",
    val port: String = ServerEndpoint.DEFAULT_PORT.toString(),
    val hostError: ManualFieldError? = null,
    val portError: ManualFieldError? = null,
) {
    val isEditing: Boolean
        get() = editingId != null
}

/**
 * Everything the connection screen renders.
 *
 * Immutable, produced only by [ConnectionViewModel], and free of sockets, protocol types, or
 * platform resources.
 */
data class ConnectionUiState(
    val permission: PermissionUiState = PermissionUiState.Checking,
    val scanPhase: ScanPhase = ScanPhase.Idle,
    val discovered: List<ServerCardUiState> = emptyList(),
    val saved: List<ServerCardUiState> = emptyList(),
    val link: LinkUiState? = null,
    val failure: FailureUiState? = null,
    val manualEntry: ManualEntryUiState? = null,
    val discoveryError: ConnectionError? = null,
) {
    val isDemoSession: Boolean
        get() = link?.target?.mode == ConnectionMode.DEMO && link.phase == LinkPhase.Connected
}

/** One-shot effects that must not be replayed by recomposition. */
sealed interface ConnectionEvent {
    data object OpenDrive : ConnectionEvent
}
