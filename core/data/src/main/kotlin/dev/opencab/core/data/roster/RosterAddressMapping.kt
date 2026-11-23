package dev.opencab.core.data.roster

import dev.opencab.core.data.throttle.ThrottleSlotUiState
import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.protocol.AddressType
import dev.opencab.core.protocol.LocoAddress

/** Domain address to the wire-facing protocol address. */
fun RosterAddress.toLocoAddress(): LocoAddress = LocoAddress(
    type = if (type == RosterAddressType.LONG) AddressType.LONG else AddressType.SHORT,
    number = number,
)

/**
 * Protocol address to the stricter roster address. Returns `null` for values the roster cannot
 * represent (notably a short address above 127).
 */
fun LocoAddress.toRosterAddressOrNull(): RosterAddress? {
    val rosterType = if (type == AddressType.LONG) RosterAddressType.LONG else RosterAddressType.SHORT
    return runCatching { RosterAddress(number = number, type = rosterType) }.getOrNull()
}

/** The roster address of an engine slot, or `null` when the wire value is outside roster rules. */
fun ThrottleSlotUiState.toRosterAddressOrNull(): RosterAddress? = address.toRosterAddressOrNull()
