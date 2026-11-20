package dev.opencab.core.protocol.codec

import dev.opencab.core.protocol.AddressType
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ThrottleKey

/** Exact delimiters used by the WiThrottle protocol. */
internal object WireSyntax {
    const val SEPARATOR: String = "<;>"
    const val LIST_SEPARATOR: String = "]\\["
    const val FIELD_SEPARATOR: String = "}|{"
    const val CONSIST_SEPARATOR: String = "<:>"
}

/** Shared, non-throwing parsers for WiThrottle wire fragments. */
internal object WireParsing {
    fun parseThrottleKey(value: String): ThrottleKey? {
        if (value.length != 1 || !ThrottleKey.isValid(value[0])) return null
        return ThrottleKey(value[0])
    }

    fun parseAddress(value: String): LocoAddress? {
        if (value.length < 2) return null
        val type =
            when (value[0]) {
                'S' -> AddressType.SHORT
                'L' -> AddressType.LONG
                else -> return null
            }
        val number = value.substring(1).toIntOrNull()
            ?.takeIf { it in LocoAddress.MIN_NUMBER..LocoAddress.MAX_NUMBER }
            ?: return null
        return LocoAddress(type, number)
    }

    fun parseLocoId(value: String): LocoId? {
        if (value == "*") return LocoId.All
        return parseAddress(value)?.let(LocoId::Addressed)
    }

    /**
     * Parses the consist-list address form `74(S)`/`3374(L)`. The `S74`/`L74` form is also
     * accepted because the protocol uses it in client commands and some servers are inconsistent.
     */
    fun parseConsistAddress(value: String): LocoAddress? {
        if (value.length > 3 && value.endsWith("(S)")) {
            return value.dropLast(3).toIntOrNull()?.let(LocoAddress::short)
        }
        if (value.length > 3 && value.endsWith("(L)")) {
            return value.dropLast(3).toIntOrNull()?.let(LocoAddress::long)
        }
        return parseAddress(value)
    }

    fun splitOnce(
        value: String,
        delimiter: String,
    ): Pair<String, String?> {
        val index = value.indexOf(delimiter)
        return if (index < 0) {
            value to null
        } else {
            value.substring(0, index) to value.substring(index + delimiter.length)
        }
    }
}
