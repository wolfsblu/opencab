package dev.opencab.core.protocol.codec

import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.codec.WireSyntax.CONSIST_SEPARATOR
import dev.opencab.core.protocol.codec.WireSyntax.SEPARATOR

internal object ConsistCommandDecoder {
    fun decode(line: String): WiThrottleCommand? {
        val rest = line.substring(3)
        return when {
            line.startsWith("RC+") -> decodeAdd(rest)
            line.startsWith("RC-") -> decodeRemoveLoco(rest)
            line.startsWith("RCP") -> decodeSetOrder(rest)
            line.startsWith("RCR") -> decodeDelete(rest)
            else -> null
        }
    }

    private fun decodeAdd(rest: String): WiThrottleCommand? {
        val (header, locoPart) = WireParsing.splitOnce(rest, CONSIST_SEPARATOR)
        val headerParts = header.split(SEPARATOR)
        val locoParts = locoPart?.split(SEPARATOR)
        if (headerParts.size < 3 || locoParts == null || locoParts.size != 2) return null
        val consist = WireParsing.parseConsistAddress(headerParts[1]) ?: return null
        val loco = WireParsing.parseConsistAddress(locoParts[0]) ?: return null
        val forward = parseBoolean(locoParts[1])
        return forward?.let { WiThrottleCommand.ConsistAdd(consist, headerParts[2], loco, it) }
    }

    private fun decodeRemoveLoco(rest: String): WiThrottleCommand? {
        val (header, locoPart) = WireParsing.splitOnce(rest, CONSIST_SEPARATOR)
        val consist = decodeAddressFromHeader(header) ?: return null
        val loco = locoPart?.let(WireParsing::parseConsistAddress) ?: return null
        return WiThrottleCommand.ConsistRemoveLoco(consist, loco)
    }

    private fun decodeSetOrder(rest: String): WiThrottleCommand? {
        val (header, locoPart) = WireParsing.splitOnce(rest, CONSIST_SEPARATOR)
        val consist = decodeAddressFromHeader(header) ?: return null
        if (locoPart.isNullOrEmpty()) return null
        val locos = locoPart.split(SEPARATOR).map { WireParsing.parseConsistAddress(it) }
        if (locos.any { it == null }) return null
        return WiThrottleCommand.ConsistSetOrder(consist, locos.filterNotNull())
    }

    private fun decodeDelete(rest: String): WiThrottleCommand? {
        val consist = decodeAddressFromHeader(rest) ?: return null
        return WiThrottleCommand.ConsistDelete(consist)
    }

    private fun decodeAddressFromHeader(header: String): LocoAddress? {
        val address = header.removePrefix(SEPARATOR)
        return WireParsing.parseConsistAddress(address)
    }

    private fun parseBoolean(value: String): Boolean? = when (value.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
