package dev.opencab.core.protocol.codec

import dev.opencab.core.protocol.TrackPowerState
import dev.opencab.core.protocol.WiThrottleCommand

/**
 * Parses canonical client-to-server command lines. This mirrors [WiThrottleEncoder] and is used by
 * round-trip tests and by replay/diagnostic tooling. It is intentionally tolerant of a few
 * documented variants (for example `r`/`d` as actions).
 */
object WiThrottleCommandDecoder {
    fun decode(line: String): WiThrottleCommand? = when {
        line.startsWith("HU") -> decodeHardwareId(line)

        line.startsWith("N") -> WiThrottleCommand.SetDeviceName(line.substring(1))

        line == "*" -> WiThrottleCommand.Heartbeat

        line == "*+" -> WiThrottleCommand.EnableHeartbeatMonitoring

        line == "*-" -> WiThrottleCommand.DisableHeartbeatMonitoring

        line == "Q" -> WiThrottleCommand.Quit

        line.startsWith("PPA") -> decodeTrackPower(line)

        line.startsWith("M") -> MultiThrottleCommandDecoder.decode(line)

        line.startsWith("RC+") || line.startsWith("RC-") || line.startsWith("RCP") ||
            line.startsWith("RCR") -> ConsistCommandDecoder.decode(line)

        else -> null
    }

    private fun decodeHardwareId(line: String): WiThrottleCommand? {
        val id = line.substring(2)
        if (id.isEmpty()) return null
        return WiThrottleCommand.SetHardwareId(id)
    }

    private fun decodeTrackPower(line: String): WiThrottleCommand? {
        val state = line.takeIf { it.length == 4 }?.get(3)?.let(TrackPowerState::fromWire)
        if (state == null || !state.canSet) return null
        return WiThrottleCommand.SetTrackPower(state)
    }
}
