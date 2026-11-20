package dev.opencab.core.protocol.codec

import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ProtocolLogger
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.SpeedStepMode
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.TrackPowerState
import dev.opencab.core.protocol.WiThrottleEvent
import dev.opencab.core.protocol.codec.WireSyntax.LIST_SEPARATOR
import dev.opencab.core.protocol.codec.WireSyntax.SEPARATOR

/**
 * Incremental decoder for the server-to-client WiThrottle protocol.
 *
 * Feed it arbitrary socket chunks. It frames complete lines, handles partial frames and multiple
 * lines per read, and converts every unrecognised or malformed line into [WiThrottleEvent.Unknown]
 * while logging the reason. It never throws for wire input.
 */
class WiThrottleDecoder(
    private val logger: ProtocolLogger = ProtocolLogger.None,
) {
    private val framer = LineFramer()

    /** Decodes every complete line in [chunk]. A trailing partial line stays buffered. */
    fun feed(chunk: ByteArray): List<WiThrottleEvent> = framer.feed(chunk).mapNotNull(::decode)

    /** Convenience overload for tests and captured text transcripts. */
    fun feed(text: String): List<WiThrottleEvent> = feed(text.toByteArray(Charsets.UTF_8))

    /** Discards any buffered partial frame. Call when reconnecting to a new server. */
    fun reset() {
        framer.reset()
    }

    /**
     * Parses one complete line. Returns `null` only for an empty line. Unknown and malformed lines
     * become [WiThrottleEvent.Unknown].
     */
    fun decode(line: String): WiThrottleEvent? {
        if (line.isEmpty()) return null
        return try {
            parse(line)
        } catch (_: IllegalArgumentException) {
            unknown(line, "malformed message")
        } catch (_: IndexOutOfBoundsException) {
            unknown(line, "malformed message")
        }
    }

    private fun parse(line: String): WiThrottleEvent = when {
        line.startsWith("VN") -> parseVersion(line)
        line.startsWith("PPA") -> parseTrackPower(line)
        line.startsWith("HT") -> parseServerType(line)
        line.startsWith("Ht") -> parseServerDescription(line)
        line.startsWith("HM") -> WiThrottleEvent.Alert(line.substring(2))
        line.startsWith("Hm") -> WiThrottleEvent.Info(line.substring(2))
        line.startsWith("RL") -> parseRoster(line)
        line.startsWith("RCC") -> parseConsistHeader(line, editable = true)
        line.startsWith("RCL") -> parseConsistHeader(line, editable = false)
        line.startsWith("RCD") -> parseConsistEntry(line)
        line.startsWith("PW") -> parseWebPort(line)
        line.startsWith("PFT") -> parseFastClock(line)
        line.startsWith("M") -> parseMultiThrottle(line)
        line.startsWith("*") -> parseHeartbeatInterval(line)
        else -> unsupported(line)
    }

    private fun parseVersion(line: String): WiThrottleEvent {
        val version = ProtocolVersion.parse(line.substring(2)) ?: return unknown(line, "malformed VN")
        return WiThrottleEvent.Version(version)
    }

    private fun parseTrackPower(line: String): WiThrottleEvent {
        val state = line.takeIf { it.length == 4 }?.get(3)?.let(TrackPowerState::fromWire)
        return state?.let(WiThrottleEvent::TrackPower) ?: unknown(line, "malformed PPA")
    }

    private fun parseServerType(line: String): WiThrottleEvent {
        if (line.length <= 2) return unknown(line, "malformed HT")
        return WiThrottleEvent.ServerType(line.substring(2))
    }

    private fun parseServerDescription(line: String): WiThrottleEvent {
        if (line.length <= 2) return unknown(line, "malformed Ht")
        return WiThrottleEvent.ServerDescription(line.substring(2))
    }

    private fun parseWebPort(line: String): WiThrottleEvent {
        val port = line.substring(2).toIntOrNull()
        if (port == null || port !in MIN_PORT..MAX_PORT) return unknown(line, "malformed PW")
        return WiThrottleEvent.WebPort(port)
    }

    private fun parseHeartbeatInterval(line: String): WiThrottleEvent {
        val seconds = line.substring(1).toIntOrNull()
        if (seconds == null || seconds < 0) return unknown(line, "malformed heartbeat")
        return WiThrottleEvent.HeartbeatInterval(seconds)
    }

    private fun parseFastClock(line: String): WiThrottleEvent {
        val (secondsText, ratioText) = WireParsing.splitOnce(line.substring(3), SEPARATOR)
        val seconds = secondsText.toLongOrNull() ?: return unknown(line, "malformed PFT")
        val ratio = ratioText?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (ratioText != null && ratioText.isNotEmpty() && ratio == null) {
            return unknown(line, "malformed PFT")
        }
        return WiThrottleEvent.FastClock(seconds, ratio)
    }

    private fun parseRoster(line: String): WiThrottleEvent {
        val body = line.substring(2)
        if (body == "0") return WiThrottleEvent.RosterList(0, emptyList())
        val (countText, entriesText) = WireParsing.splitOnce(body, LIST_SEPARATOR)
        val count = countText.toIntOrNull()
        if (count == null || count < 0 || entriesText == null) return unknown(line, "malformed RL")
        val entries = GreetingListDecoder.rosterEntries(entriesText)
            ?: return unknown(line, "malformed RL entry")
        return WiThrottleEvent.RosterList(count, entries)
    }

    private fun parseConsistHeader(
        line: String,
        editable: Boolean,
    ): WiThrottleEvent {
        val count = line.substring(3).toIntOrNull()
        if (count == null || count < 0) return unknown(line, "malformed consist header")
        return WiThrottleEvent.ConsistListHeader(count, editable)
    }

    private fun parseConsistEntry(line: String): WiThrottleEvent {
        val consist = GreetingListDecoder.consistEntry(line)
            ?: return unknown(line, "malformed RCD")
        return WiThrottleEvent.ConsistEntry(consist)
    }

    private fun parseMultiThrottle(line: String): WiThrottleEvent {
        if (line.length < 3) return unknown(line, "malformed M")
        val key = WireParsing.parseThrottleKey(line.substring(1, 2))
            ?: return unknown(line, "malformed throttle key")
        val command = line[2]
        val rest = line.substring(3)
        return when (command) {
            '+' -> parseLocoNotification(line, key, rest, added = true)
            '-' -> parseLocoNotification(line, key, rest, added = false)
            'S' -> parseStealRequested(line, key, rest)
            'L' -> parseFunctionLabels(line, key, rest)
            'A' -> parseAction(line, key, rest)
            else -> unknown(line, "unknown multi-throttle command")
        }
    }

    private fun parseLocoNotification(
        line: String,
        key: ThrottleKey,
        rest: String,
        added: Boolean,
    ): WiThrottleEvent {
        val (locoPart, _) = WireParsing.splitOnce(rest, SEPARATOR)
        val locoId = WireParsing.parseLocoId(locoPart) ?: return unknown(line, "malformed loco id")
        return if (added) {
            WiThrottleEvent.LocoAdded(key, locoId)
        } else {
            WiThrottleEvent.LocoRemoved(key, locoId)
        }
    }

    private fun parseStealRequested(
        line: String,
        key: ThrottleKey,
        rest: String,
    ): WiThrottleEvent {
        val (locoPart, payload) = WireParsing.splitOnce(rest, SEPARATOR)
        val locoId = WireParsing.parseLocoId(locoPart) ?: return unknown(line, "malformed steal loco")
        if (!payload.isNullOrEmpty() && WireParsing.parseLocoId(payload) == null) {
            return unknown(line, "malformed steal payload")
        }
        return WiThrottleEvent.StealRequested(key, locoId)
    }

    private fun parseFunctionLabels(
        line: String,
        key: ThrottleKey,
        rest: String,
    ): WiThrottleEvent {
        val (locoPart, payload) = WireParsing.splitOnce(rest, SEPARATOR)
        val locoId = WireParsing.parseLocoId(locoPart) ?: return unknown(line, "malformed label loco")
        return WiThrottleEvent.FunctionLabels(key, locoId, parseLabels(payload.orEmpty()))
    }

    private fun parseLabels(payload: String): List<String> {
        val parts = payload.split(LIST_SEPARATOR)
        return if (parts.firstOrNull().isNullOrEmpty()) parts.drop(1) else parts
    }

    private fun parseAction(
        line: String,
        key: ThrottleKey,
        rest: String,
    ): WiThrottleEvent {
        val (locoPart, payload) = WireParsing.splitOnce(rest, SEPARATOR)
        val locoId = WireParsing.parseLocoId(locoPart) ?: return unknown(line, "malformed action loco")
        if (payload.isNullOrEmpty()) return unknown(line, "missing action payload")
        return when (payload[0]) {
            'V' -> parseSpeed(line, key, locoId, payload)
            'R' -> parseDirection(line, key, locoId, payload)
            's' -> parseSpeedStepMode(line, key, locoId, payload)
            'F', 'f' -> parseFunctionState(line, key, locoId, payload)
            'm' -> parseFunctionMomentary(line, key, locoId, payload)
            else -> unknown(line, "unknown multi-throttle action")
        }
    }

    private fun parseSpeed(
        line: String,
        key: ThrottleKey,
        locoId: LocoId,
        payload: String,
    ): WiThrottleEvent {
        val rawSpeed = payload.substring(1).toIntOrNull() ?: return unknown(line, "malformed V")
        if (rawSpeed > MAX_SPEED) return unknown(line, "speed out of range")
        val emergencyStop = rawSpeed < 0
        val speed = if (emergencyStop) 0 else rawSpeed
        return WiThrottleEvent.SpeedChanged(key, locoId, speed, emergencyStop)
    }

    private fun parseDirection(
        line: String,
        key: ThrottleKey,
        locoId: LocoId,
        payload: String,
    ): WiThrottleEvent {
        val value = payload.drop(1)
        if (value.isEmpty()) return unknown(line, "malformed R")
        return WiThrottleEvent.DirectionChanged(key, locoId, Direction.fromWire(value[0]))
    }

    private fun parseSpeedStepMode(
        line: String,
        key: ThrottleKey,
        locoId: LocoId,
        payload: String,
    ): WiThrottleEvent {
        val value = payload.drop(1)
        if (value.isEmpty()) return unknown(line, "malformed s")
        return WiThrottleEvent.SpeedStepModeChanged(key, locoId, SpeedStepMode.fromWire(value))
    }

    private fun parseFunctionState(
        line: String,
        key: ThrottleKey,
        locoId: LocoId,
        payload: String,
    ): WiThrottleEvent {
        val body = payload.drop(1)
        val number = parseFunctionNumber(body)
        if (number == null) return unknown(line, "malformed function")
        return WiThrottleEvent.FunctionStateChanged(key, locoId, number, body[0] == '1')
    }

    private fun parseFunctionMomentary(
        line: String,
        key: ThrottleKey,
        locoId: LocoId,
        payload: String,
    ): WiThrottleEvent {
        val body = payload.drop(1)
        val number = parseFunctionNumber(body)
        if (number == null) return unknown(line, "malformed momentary function")
        return WiThrottleEvent.FunctionMomentaryChanged(key, locoId, number, body[0] == '1')
    }

    private fun parseFunctionNumber(body: String): Int? {
        if (body.length < 2 || (body[0] != '0' && body[0] != '1')) return null
        val number = body.substring(1).toIntOrNull() ?: return null
        return number.takeIf { it in MIN_FUNCTION..MAX_FUNCTION }
    }

    private fun unsupported(line: String): WiThrottleEvent.Unknown {
        val reason =
            when {
                line.startsWith("PT") -> "unsupported turnout message"
                line.startsWith("PR") -> "unsupported route message"
                line.startsWith("PFC") -> "unsupported LnWI message"
                line.startsWith("RF") || line.startsWith("RS") -> "unsupported legacy throttle message"
                line.startsWith("RPF") || line.startsWith("RSF") -> "unsupported legacy throttle message"
                else -> "unknown message type"
            }
        return unknown(line, reason)
    }

    private fun unknown(
        line: String,
        reason: String,
    ): WiThrottleEvent.Unknown {
        logger.warn("WiThrottle decoder: $reason: ${line.take(MAX_LOG_LENGTH)}")
        return WiThrottleEvent.Unknown(line, reason)
    }

    private companion object {
        const val MIN_PORT = 0
        const val MAX_PORT = 65535
        const val MAX_SPEED = 126
        const val MIN_FUNCTION = 0
        const val MAX_FUNCTION = 31
        const val MAX_LOG_LENGTH = 256
    }
}
