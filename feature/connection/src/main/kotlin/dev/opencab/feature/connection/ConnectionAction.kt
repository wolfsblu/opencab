package dev.opencab.feature.connection

/**
 * Every user intent the connection screen can emit.
 *
 * The marker categories keep the route's dispatch split into small, readable handlers instead of
 * one large `when` and let the compiler keep each handler exhaustive.
 */
internal sealed interface ConnectionAction {
    /** Leaves or changes the screen. */
    sealed interface Navigation : ConnectionAction

    /** Talks to the platform permission APIs or app settings. */
    sealed interface Permission : ConnectionAction

    /** Acts on a discovered or saved server row. */
    sealed interface Server : ConnectionAction

    /** Starts, retries, or ends the active link. */
    sealed interface Link : ConnectionAction

    /** Edits the manual-entry sheet. */
    sealed interface Manual : ConnectionAction

    data object Back : Navigation

    data object Rescan : Server

    data class Connect(val server: ServerCardUiState) : Server

    data class EditSaved(val server: ServerCardUiState) : Server

    data class DeleteSaved(val server: ServerCardUiState) : Server

    data object CancelConnection : Link

    data object Retry : Link

    data object DismissFailure : Link

    data object Disconnect : Link

    data object RequestPermission : Permission

    data object OpenSettings : Permission

    data object OpenDrive : Navigation

    data object OpenManualEntry : Manual

    data object CancelManualEntry : Manual

    data object SubmitManualEntry : Manual

    data object StartDemo : Link

    data object EndDemo : Link
}

/** Stable tags used by Compose UI tests and previews. */
object ConnectionTestTags {
    const val SCREEN: String = "screen_connection"
    const val SCANNING: String = "connection_scanning"
    const val EMPTY: String = "connection_empty"
    const val PERMISSION: String = "connection_permission"
    const val PERMISSION_DENIED: String = "connection_permission_denied"
    const val PERMISSION_ALLOW: String = "connection_permission_allow"
    const val PERMISSION_SETTINGS: String = "connection_permission_settings"
    const val CONNECTING: String = "connection_connecting"
    const val RECONNECTING: String = "connection_reconnecting"
    const val FAILED: String = "connection_failed"
    const val CONNECTED: String = "connection_connected"
    const val DEMO_BANNER: String = "connection_demo_banner"
    const val MANUAL_SHEET: String = "connection_manual_sheet"
    const val MANUAL_HOST: String = "connection_manual_host"
    const val MANUAL_PORT: String = "connection_manual_port"
    const val MANUAL_SUBMIT: String = "connection_manual_submit"
    const val MANUAL_ENTRY: String = "connection_manual_entry"
    const val START_DEMO: String = "connection_start_demo"
    const val SERVER_LIST: String = "connection_server_list"
    const val OPEN_DRIVE: String = "connection_open_drive"
    const val DISCONNECT: String = "connection_disconnect"
    const val RETRY: String = "connection_retry"
    const val CANCEL: String = "connection_cancel"
}
