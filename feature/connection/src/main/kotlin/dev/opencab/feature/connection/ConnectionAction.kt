package dev.opencab.feature.connection

/** Every user intent the connection screen can emit. */
internal sealed interface ConnectionAction {
    data object Back : ConnectionAction

    data object Rescan : ConnectionAction

    data class Connect(val server: ServerCardUiState) : ConnectionAction

    data class EditSaved(val server: ServerCardUiState) : ConnectionAction

    data class DeleteSaved(val server: ServerCardUiState) : ConnectionAction

    data object CancelConnection : ConnectionAction

    data object Retry : ConnectionAction

    data object DismissFailure : ConnectionAction

    data object RequestPermission : ConnectionAction

    data object OpenSettings : ConnectionAction

    data object OpenDrive : ConnectionAction

    data object OpenManualEntry : ConnectionAction

    data object CancelManualEntry : ConnectionAction

    data object SubmitManualEntry : ConnectionAction

    data object StartDemo : ConnectionAction

    data object EndDemo : ConnectionAction
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
    const val RETRY: String = "connection_retry"
    const val CANCEL: String = "connection_cancel"
}
