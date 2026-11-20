package dev.opencab.core.protocol

/** Whether a DCC address is transmitted as a short or long address. */
enum class AddressType {
    SHORT,
    LONG,
}

/**
 * A DCC locomotive address in WiThrottle wire form (`S3` or `L341`).
 *
 * The codec accepts 0..10239 for both address types so that unusual server values can still be
 * represented. Short-vs-long range validation belongs to the roster and address-entry layers.
 */
data class LocoAddress(
    val type: AddressType,
    val number: Int,
) {
    init {
        require(number in MIN_NUMBER..MAX_NUMBER) { "DCC address out of range: $number" }
    }

    fun toWire(): String = (if (type == AddressType.LONG) "L" else "S") + number

    companion object {
        const val MIN_NUMBER: Int = 0
        const val MAX_NUMBER: Int = 10239

        fun short(number: Int): LocoAddress = LocoAddress(AddressType.SHORT, number)

        fun long(number: Int): LocoAddress = LocoAddress(AddressType.LONG, number)
    }
}

/** A locomotive reference used by a throttle action. `*` addresses every loco on the throttle. */
sealed interface LocoId {
    data class Addressed(val address: LocoAddress) : LocoId

    data object All : LocoId

    fun toWire(): String = when (this) {
        is Addressed -> address.toWire()
        All -> "*"
    }

    companion object {
        fun of(address: LocoAddress): LocoId = Addressed(address)
    }
}

/**
 * The single-character key that identifies a multi-throttle instance.
 *
 * The protocol documents `0`-`9`, `T`, `S`, and `G`. JMRI accepts any non-delimiter character, so
 * the codec preserves whatever the server sends instead of rejecting unfamiliar keys.
 */
@JvmInline
value class ThrottleKey(val value: Char) {
    init {
        require(isValid(value)) { "Invalid throttle key: $value" }
    }

    override fun toString(): String = value.toString()

    companion object {
        val DEFAULT: ThrottleKey = ThrottleKey('0')

        fun isValid(value: Char): Boolean = !value.isWhitespace() && value !in "<>;*"
    }
}

/** How an acquire request selects a locomotive: a DCC address or a JMRI roster entry id. */
sealed interface LocoSelection {
    data class Address(val address: LocoAddress) : LocoSelection

    data class RosterEntry(val id: String) : LocoSelection {
        init {
            requireSingleLine(id, "roster entry id")
        }
    }

    fun toWire(): String = when (this) {
        is Address -> address.toWire()
        is RosterEntry -> "E$id"
    }
}
