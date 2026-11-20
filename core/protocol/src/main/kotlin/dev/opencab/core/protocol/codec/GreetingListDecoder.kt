package dev.opencab.core.protocol.codec

import dev.opencab.core.protocol.AddressType
import dev.opencab.core.protocol.ConsistInfo
import dev.opencab.core.protocol.ConsistMember
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.RosterLoco
import dev.opencab.core.protocol.codec.WireSyntax.FIELD_SEPARATOR
import dev.opencab.core.protocol.codec.WireSyntax.LIST_SEPARATOR

internal object GreetingListDecoder {
    fun rosterEntries(text: String): List<RosterLoco>? {
        val entries = mutableListOf<RosterLoco>()
        for (part in text.split(LIST_SEPARATOR)) {
            if (part.isEmpty()) continue
            entries += rosterEntry(part) ?: return null
        }
        return entries
    }

    fun consistEntry(line: String): ConsistInfo? {
        if (!line.startsWith("RCD$FIELD_SEPARATOR")) return null
        val (header, membersText) = WireParsing.splitOnce(line.substring(3), LIST_SEPARATOR)
        val headerParts = header.split(FIELD_SEPARATOR)
        val consistAddress = headerParts.getOrNull(1)?.let(WireParsing::parseConsistAddress) ?: return null
        val id = headerParts.getOrNull(2).orEmpty()
        val members =
            if (membersText.isNullOrEmpty()) {
                emptyList()
            } else {
                consistMembers(membersText) ?: return null
            }
        return ConsistInfo(consistAddress, id, members)
    }

    private fun rosterEntry(part: String): RosterLoco? {
        val fields = part.split(FIELD_SEPARATOR)
        if (fields.size != 3) return null
        val number = fields[1].toIntOrNull()?.takeIf { it in LocoAddress.MIN_NUMBER..LocoAddress.MAX_NUMBER }
            ?: return null
        val type =
            when (fields[2]) {
                "S" -> AddressType.SHORT
                "L" -> AddressType.LONG
                else -> return null
            }
        return RosterLoco(fields[0], LocoAddress(type, number))
    }

    private fun consistMembers(text: String): List<ConsistMember>? {
        val members = mutableListOf<ConsistMember>()
        for (part in text.split(LIST_SEPARATOR)) {
            if (part.isEmpty()) continue
            members += consistMember(part) ?: return null
        }
        return members
    }

    private fun consistMember(part: String): ConsistMember? {
        val fields = part.split(FIELD_SEPARATOR)
        if (fields.size != 2) return null
        val address = WireParsing.parseConsistAddress(fields[0]) ?: return null
        val forward =
            when (fields[1].lowercase()) {
                "true" -> true
                "false" -> false
                else -> return null
            }
        return ConsistMember(address, forward)
    }
}
