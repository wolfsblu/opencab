package dev.opencab.core.model

/** How a DCC address is transmitted. Short addresses must fit 1..127; long addresses 1..10239. */
enum class RosterAddressType {
    SHORT,
    LONG,
}

/**
 * A validated DCC address owned by the roster.
 *
 * Both address types share the 1..127 numbers. A number in that overlap may be a short or a
 * long address; [RosterAddressRules.validate] defaults it to short and flags the ambiguity.
 */
data class RosterAddress(
    val number: Int,
    val type: RosterAddressType,
) {
    init {
        require(number in RosterAddressRules.MIN_NUMBER..RosterAddressRules.MAX_NUMBER) {
            "DCC address out of range: $number"
        }
        if (type == RosterAddressType.SHORT) {
            require(number in RosterAddressRules.SHORT_MIN..RosterAddressRules.SHORT_MAX) {
                "Short DCC address out of range: $number"
            }
        }
    }

    /** `S3` or `L3412`, the compact form used by the protocol and the roster rows. */
    val wireText: String
        get() = if (type == RosterAddressType.LONG) "L$number" else "S$number"

    /** `3 · short` or `3412 · long`, for readouts that must not hide the address type. */
    val displayText: String
        get() = "$number · ${if (type == RosterAddressType.LONG) "long" else "short"}"
}

enum class AddressValidationError {
    REQUIRED,
    NOT_A_NUMBER,
    OUT_OF_RANGE,
    SHORT_OUT_OF_RANGE,
    LONG_OUT_OF_RANGE,
}

sealed interface AddressValidation {
    /** The parsed address, or `null` when the input did not validate. */
    val address: RosterAddress?

    val error: AddressValidationError?

    val isValid: Boolean
        get() = error == null

    data class Valid(
        override val address: RosterAddress,
        /**
         * True when the number is in the 1..127 overlap and is being used as a long address, or
         * when the user has not chosen a type yet. The entry layer shows the overlap warning.
         */
        val isAmbiguous: Boolean,
    ) : AddressValidation {
        override val error: AddressValidationError? = null
    }

    data class Invalid(
        override val error: AddressValidationError,
        /** The type the operator could switch to to make the number valid, when one exists. */
        val suggestedType: RosterAddressType? = null,
    ) : AddressValidation {
        override val address: RosterAddress? = null
    }
}

/**
 * The single source of truth for DCC address entry.
 *
 * The rules match NMRA addressing: short 1..127, long 1..10239. Numbers 1..127 are legal in both
 * spaces, so a number without an explicit type resolves to short and the result is marked
 * ambiguous. An explicit long 1..127 remains valid but ambiguous; an explicit short above 127 is
 * an error with a suggested long type.
 */
object RosterAddressRules {
    const val MIN_NUMBER: Int = 1
    const val SHORT_MIN: Int = 1
    const val SHORT_MAX: Int = 127
    const val LONG_MIN: Int = 1
    const val LONG_MAX: Int = 10239
    const val MAX_NUMBER: Int = LONG_MAX

    /** The type used when the operator has not chosen one. */
    fun defaultType(number: Int): RosterAddressType = if (number in SHORT_MIN..SHORT_MAX) {
        RosterAddressType.SHORT
    } else {
        RosterAddressType.LONG
    }

    fun isAmbiguous(
        number: Int,
        type: RosterAddressType?,
    ): Boolean = number in SHORT_MIN..SHORT_MAX && type != RosterAddressType.SHORT

    fun validate(
        input: String,
        selectedType: RosterAddressType? = null,
    ): AddressValidation {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return AddressValidation.Invalid(AddressValidationError.REQUIRED)
        val number = trimmed.toIntOrNull()
            ?: return AddressValidation.Invalid(AddressValidationError.NOT_A_NUMBER)
        val type = selectedType ?: defaultType(number)
        return when {
            number < MIN_NUMBER -> AddressValidation.Invalid(AddressValidationError.OUT_OF_RANGE)

            number > MAX_NUMBER -> AddressValidation.Invalid(AddressValidationError.LONG_OUT_OF_RANGE)

            type == RosterAddressType.SHORT && number > SHORT_MAX -> AddressValidation.Invalid(
                error = AddressValidationError.SHORT_OUT_OF_RANGE,
                suggestedType = RosterAddressType.LONG,
            )

            else -> AddressValidation.Valid(
                address = RosterAddress(number = number, type = type),
                isAmbiguous = isAmbiguous(number, selectedType),
            )
        }
    }
}

/** The named palette a roster locomotive can use. Stored by key, not by ARGB value. */
enum class RosterColor(
    val key: String,
) {
    BLUE("blue"),
    TEAL("teal"),
    GREEN("green"),
    AMBER("amber"),
    RED("red"),
    VIOLET("violet"),
    ;

    companion object {
        fun fromKey(key: String?): RosterColor = entries.firstOrNull { it.key == key } ?: BLUE
    }
}

/** The silhouette a roster row shows. Stored by key so the UI can change the artwork later. */
enum class RosterIcon(
    val key: String,
) {
    STEAM("steam"),
    DIESEL("diesel"),
    ELECTRIC("electric"),
    SHUNTER("shunter"),
    ;

    companion object {
        fun fromKey(key: String?): RosterIcon = entries.firstOrNull { it.key == key } ?: STEAM
    }
}

/** A locomotive in the local roster. */
data class RosterLoco(
    val id: String,
    val name: String,
    val address: RosterAddress,
    val localFunctionLabels: Map<Int, String> = emptyMap(),
    val serverFunctionLabels: Map<Int, String> = emptyMap(),
    val isFavorite: Boolean = false,
    val color: RosterColor = RosterColor.BLUE,
    val icon: RosterIcon = RosterIcon.STEAM,
    val lastUsedEpochMillis: Long? = null,
    /** JMRI roster entry id when this loco came from the layout roster. */
    val serverRosterId: String? = null,
    val createdAtEpochMillis: Long = 0L,
) {
    val displayName: String
        get() = name.trim().ifBlank { "Loco ${address.number}" }

    /**
     * Local labels always win. A server label is the fallback, then the generic `F<n>`.
     */
    fun functionLabel(number: Int): String = localFunctionLabels[number]?.takeIf { it.isNotBlank() }
        ?: serverFunctionLabels[number]?.takeIf { it.isNotBlank() }
        ?: "F$number"
}

/** One locomotive announced by the layout's `RL` roster list. */
data class ServerRosterEntry(
    val id: String,
    val name: String,
    val address: RosterAddress,
)

/** The editable fields of a roster entry. */
data class RosterLocoDraft(
    val name: String,
    val address: RosterAddress,
    val localFunctionLabels: Map<Int, String> = emptyMap(),
    val isFavorite: Boolean = false,
    val color: RosterColor = RosterColor.BLUE,
    val icon: RosterIcon = RosterIcon.STEAM,
    val serverRosterId: String? = null,
)
