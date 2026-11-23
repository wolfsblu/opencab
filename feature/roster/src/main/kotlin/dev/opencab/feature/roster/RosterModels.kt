package dev.opencab.feature.roster

import dev.opencab.core.model.AddressValidation
import dev.opencab.core.model.AddressValidationError
import dev.opencab.core.model.RosterAddressRules
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.model.RosterColor
import dev.opencab.core.model.RosterIcon
import dev.opencab.core.model.RosterLoco
import dev.opencab.core.model.RosterLocoDraft

/** A roster row, already reduced to display values. */
data class RosterItemUi(
    val id: String,
    val name: String,
    val addressLabel: String,
    val addressDisplay: String,
    val isFavorite: Boolean,
    val color: RosterColor,
    val icon: RosterIcon,
    val isActive: Boolean,
    val lastUsedEpochMillis: Long?,
)

data class RosterUiState(
    val isLoading: Boolean = true,
    val locos: List<RosterItemUi> = emptyList(),
    val serverRosterCount: Int = 0,
    val importedCount: Int? = null,
    val importEnabled: Boolean = RosterFeatureFlags.JMRI_ROSTER_IMPORT_ENABLED,
) {
    val isEmpty: Boolean
        get() = !isLoading && locos.isEmpty()
}

/** The roster screen asks the app to reveal Drive after a successful acquisition. */
enum class RosterEvent {
    OpenDrive,
}

internal fun RosterLoco.toItem(activeAddressKey: String?): RosterItemUi = RosterItemUi(
    id = id,
    name = displayName,
    addressLabel = address.wireText,
    addressDisplay = address.displayText,
    isFavorite = isFavorite,
    color = color,
    icon = icon,
    isActive = address.wireText == activeAddressKey,
    lastUsedEpochMillis = lastUsedEpochMillis,
)

internal fun List<RosterLoco>.toRosterItems(activeAddressKey: String?): List<RosterItemUi> = this
    .sortedWith(
        compareByDescending<RosterLoco> { it.isFavorite }
            .thenByDescending { it.lastUsedEpochMillis ?: Long.MIN_VALUE }
            .thenBy { it.displayName.lowercase() },
    )
    .map { it.toItem(activeAddressKey) }

enum class PickerSectionKind {
    Favorites,
    Recents,
    All,
    Matches,
}

data class PickerSectionUi(
    val kind: PickerSectionKind,
    val locos: List<RosterLoco>,
)

enum class PickerMode {
    List,
    AddAddress,
}

data class PickerAddForm(
    val name: String = "",
    val address: String = "",
    /** `null` means resolve the type from the number (short inside 1..127, long above). */
    val type: RosterAddressType? = null,
)

sealed interface PickerAddError {
    data class Duplicate(val existing: RosterLoco) : PickerAddError

    data class InvalidAddress(val error: AddressValidationError) : PickerAddError
}

data class LocoPickerUiState(
    val query: String = "",
    val mode: PickerMode = PickerMode.List,
    val allLocos: List<RosterLoco> = emptyList(),
    val sections: List<PickerSectionUi> = emptyList(),
    val activeLocoId: String? = null,
    val form: PickerAddForm = PickerAddForm(),
    val validation: AddressValidation = AddressValidation.Invalid(AddressValidationError.REQUIRED),
    val addError: PickerAddError? = null,
    val isSaving: Boolean = false,
    val serverRosterCount: Int = 0,
    val importEnabled: Boolean = RosterFeatureFlags.JMRI_ROSTER_IMPORT_ENABLED,
) {
    val isEmpty: Boolean
        get() = allLocos.isEmpty()

    val hasQuery: Boolean
        get() = query.isNotBlank()
}

/** The picker closes after a selection, however the acquisition turns out. */
data class PickerResult(
    val locoId: String,
    val wasAlreadyActive: Boolean,
)

/**
 * Pure filtering and grouping for the picker. Recents and favorites are separate groups so one
 * locomotive never appears twice in the same list.
 */
object RosterSearch {
    private const val RECENT_LIMIT = 4

    fun sections(
        locos: List<RosterLoco>,
        query: String,
        recentLimit: Int = RECENT_LIMIT,
    ): List<PickerSectionUi> {
        if (locos.isEmpty()) return emptyList()
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            val matches = filter(locos, trimmed)
            return if (matches.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    PickerSectionUi(PickerSectionKind.Matches, matches),
                )
            }
        }
        val favorites = locos.filter { it.isFavorite }.sortedBy { it.displayName.lowercase() }
        val favoriteIds = favorites.map { it.id }.toSet()
        val recents = locos
            .filter { it.id !in favoriteIds && it.lastUsedEpochMillis != null }
            .sortedByDescending { it.lastUsedEpochMillis }
            .take(recentLimit)
        val recentIds = recents.map { it.id }.toSet()
        val rest = locos
            .filter { it.id !in favoriteIds && it.id !in recentIds }
            .sortedBy { it.displayName.lowercase() }
        return buildList {
            if (favorites.isNotEmpty()) add(PickerSectionUi(PickerSectionKind.Favorites, favorites))
            if (recents.isNotEmpty()) add(PickerSectionUi(PickerSectionKind.Recents, recents))
            if (rest.isNotEmpty()) add(PickerSectionUi(PickerSectionKind.All, rest))
        }
    }

    fun filter(
        locos: List<RosterLoco>,
        query: String,
    ): List<RosterLoco> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return locos
        return locos
            .filter { matches(it, trimmed) }
            .sortedWith(compareByDescending<RosterLoco> { it.isFavorite }.thenBy { it.displayName.lowercase() })
    }

    fun matches(
        loco: RosterLoco,
        query: String,
    ): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        val compact = trimmed.replace(" ", "")
        return loco.displayName.contains(trimmed, ignoreCase = true) ||
            loco.address.number.toString().contains(compact) ||
            loco.address.wireText.contains(compact, ignoreCase = true) ||
            loco.address.displayText.contains(trimmed, ignoreCase = true)
    }
}

/** The draft a picker form would save, or `null` when the address is invalid. */
internal fun PickerAddForm.toDraft(): RosterLocoDraft? {
    val validation = RosterAddressRules.validate(address, type)
    val parsed = validation.address ?: return null
    val resolvedName = name.trim().ifEmpty { "Loco ${parsed.number}" }
    return RosterLocoDraft(
        name = resolvedName,
        address = parsed,
    )
}
