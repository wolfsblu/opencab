package dev.opencab.feature.throttle

/** Stable tags for Compose UI tests and screenshot tooling. */
object DriveTestTags {
    const val SCREEN: String = "screen_drive"
    const val HEADER: String = "drive_header"
    const val STATUS: String = "drive_status"
    const val BANNER: String = "drive_connection_banner"
    const val ISSUE: String = "drive_issue"
    const val TAKE_CONTROL: String = "drive_take_control"
    const val RETRY_ACQUIRE: String = "drive_retry_acquire"
    const val LEVER: String = "drive_lever"
    const val HANDLE: String = "drive_lever_handle"
    const val STEP: String = "drive_step"
    const val ESTIMATE: String = "drive_estimate"
    const val NUDGE_UP: String = "drive_nudge_up"
    const val NUDGE_DOWN: String = "drive_nudge_down"
    const val STOP: String = "drive_stop"
    const val DIRECTION: String = "drive_direction"
    const val FUNCTIONS_TOGGLE: String = "drive_functions_toggle"
    const val FUNCTIONS: String = "drive_functions"
    const val MENU: String = "drive_menu"
    const val RELEASE: String = "drive_release"
    const val RELEASE_CONFIRM: String = "drive_release_confirm"
    const val CHOOSE_LOCO: String = "drive_choose_loco"
    const val EMPTY: String = "drive_empty"

    fun function(number: Int): String = "drive_function_$number"
}
