package dev.opencab.feature.roster

object RosterTestTags {
    const val SCREEN: String = "screen_roster"
    const val LIST: String = "roster_list"
    const val EMPTY: String = "roster_empty"
    const val ADD: String = "roster_add"
    const val IMPORT: String = "roster_import"
    const val IMPORT_STATUS: String = "roster_import_status"

    fun entry(id: String): String = "roster_entry_$id"

    fun favorite(id: String): String = "roster_favorite_$id"

    fun drive(id: String): String = "roster_drive_$id"

    const val EDITOR_SCREEN: String = "screen_roster_editor"
    const val EDITOR_NAME: String = "roster_editor_name"
    const val EDITOR_ADDRESS: String = "roster_editor_address"
    const val EDITOR_ERROR: String = "roster_editor_error"
    const val EDITOR_WARNING: String = "roster_editor_warning"
    const val EDITOR_CHANGE_NOTE: String = "roster_editor_change_note"
    const val EDITOR_FAVORITE: String = "roster_editor_favorite"
    const val EDITOR_FUNCTIONS_TOGGLE: String = "roster_editor_functions_toggle"
    const val EDITOR_SAVE: String = "roster_editor_save"

    fun addressType(type: String): String = "roster_editor_type_$type"

    fun color(key: String): String = "roster_editor_color_$key"

    fun icon(key: String): String = "roster_editor_icon_$key"

    fun functionLabel(number: Int): String = "roster_editor_function_$number"

    const val PICKER_SHEET: String = "loco_picker_sheet"
    const val PICKER_SEARCH: String = "loco_picker_search"
    const val PICKER_EMPTY: String = "loco_picker_empty"
    const val PICKER_ADD: String = "loco_picker_add"
    const val PICKER_ADD_FORM: String = "loco_picker_add_form"
    const val PICKER_ADD_NAME: String = "loco_picker_add_name"
    const val PICKER_ADD_ADDRESS: String = "loco_picker_add_address"
    const val PICKER_ADD_SAVE: String = "loco_picker_add_save"
    const val PICKER_ADD_CANCEL: String = "loco_picker_add_cancel"
    const val PICKER_ADD_ERROR: String = "loco_picker_add_error"
    const val PICKER_CLOSE: String = "loco_picker_close"

    fun pickerEntry(id: String): String = "loco_picker_entry_$id"
}
