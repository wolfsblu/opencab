package dev.opencab.core.protocol.codec

import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.LocoSelection
import dev.opencab.core.protocol.RemovalAction
import dev.opencab.core.protocol.SpeedStepMode
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.codec.WireSyntax.SEPARATOR

internal object MultiThrottleCommandDecoder {
    fun decode(line: String): WiThrottleCommand? {
        if (line.length < 4) return null
        val key = WireParsing.parseThrottleKey(line.substring(1, 2)) ?: return null
        val rest = line.substring(3)
        return when (line[2]) {
            '+' -> decodeAcquire(key, rest)
            '-' -> decodeRelease(key, rest)
            'S' -> decodeSteal(key, rest)
            'A' -> decodeAction(key, rest)
            else -> null
        }
    }

    private fun decodeAcquire(
        key: ThrottleKey,
        rest: String,
    ): WiThrottleCommand? {
        val (locoPart, payload) = WireParsing.splitOnce(rest, SEPARATOR)
        val locoKey = WireParsing.parseAddress(locoPart) ?: return null
        val selection =
            when {
                payload.isNullOrEmpty() -> LocoSelection.Address(locoKey)

                payload.startsWith("E") -> payload.substring(1).takeIf { it.isNotEmpty() }
                    ?.let(LocoSelection::RosterEntry)

                else -> WireParsing.parseAddress(payload)?.let(LocoSelection::Address)
            } ?: return null
        return WiThrottleCommand.Acquire(key, locoKey, selection)
    }

    private fun decodeRelease(
        key: ThrottleKey,
        rest: String,
    ): WiThrottleCommand? {
        val (locoPart, payload) = WireParsing.splitOnce(rest, SEPARATOR)
        val target = WireParsing.parseLocoId(locoPart) ?: return null
        val action =
            when (payload) {
                null, "", "r" -> RemovalAction.RELEASE
                "d" -> RemovalAction.DISPATCH
                else -> return null
            }
        return WiThrottleCommand.Release(key, target, action)
    }

    private fun decodeSteal(
        key: ThrottleKey,
        rest: String,
    ): WiThrottleCommand? {
        val (locoPart, payload) = WireParsing.splitOnce(rest, SEPARATOR)
        val locoKey = WireParsing.parseAddress(locoPart) ?: return null
        if (!payload.isNullOrEmpty() && WireParsing.parseAddress(payload) != locoKey) return null
        return WiThrottleCommand.Steal(key, locoKey)
    }

    private fun decodeAction(
        key: ThrottleKey,
        rest: String,
    ): WiThrottleCommand? {
        val (locoPart, payload) = WireParsing.splitOnce(rest, SEPARATOR)
        val target = WireParsing.parseLocoId(locoPart) ?: return null
        if (payload.isNullOrEmpty()) return null
        return decodeActionPayload(key, target, payload)
    }

    private fun decodeActionPayload(
        key: ThrottleKey,
        target: LocoId,
        payload: String,
    ): WiThrottleCommand? = when {
        payload == "X" -> WiThrottleCommand.EmergencyStop(key, target)

        payload == "I" -> WiThrottleCommand.SetIdle(key, target)

        payload == "qV" -> WiThrottleCommand.QuerySpeed(key, target)

        payload == "qR" -> WiThrottleCommand.QueryDirection(key, target)

        payload == "qs" -> WiThrottleCommand.QuerySpeedStepMode(key, target)

        payload == "qm" -> WiThrottleCommand.QueryMomentaryStates(key, target)

        payload == "r" -> WiThrottleCommand.Release(key, target, RemovalAction.RELEASE)

        payload == "d" -> WiThrottleCommand.Release(key, target, RemovalAction.DISPATCH)

        payload.startsWith("V") -> decodeSetSpeed(key, target, payload)

        payload.startsWith("R") -> decodeSetDirection(key, target, payload)

        payload.startsWith("s") -> decodeSpeedStepMode(key, target, payload)

        payload.startsWith("F") -> FunctionCommandDecoder.decodeButton(key, target, payload)

        payload.startsWith("f") -> FunctionCommandDecoder.decodeForce(key, target, payload)

        payload.startsWith("m") -> FunctionCommandDecoder.decodeMomentary(key, target, payload)

        payload.startsWith("C") -> WireParsing.parseAddress(payload.substring(1))
            ?.let { WiThrottleCommand.SetConsistLead(key, target, it) }

        else -> null
    }

    private fun decodeSetSpeed(
        key: ThrottleKey,
        target: LocoId,
        payload: String,
    ): WiThrottleCommand? {
        val speed = payload.substring(1).toIntOrNull() ?: return null
        if (speed !in WiThrottleCommand.SetSpeed.MIN_SPEED..WiThrottleCommand.SetSpeed.MAX_SPEED) {
            return null
        }
        return WiThrottleCommand.SetSpeed(key, target, speed)
    }

    private fun decodeSetDirection(
        key: ThrottleKey,
        target: LocoId,
        payload: String,
    ): WiThrottleCommand? {
        val value = payload.substring(1)
        if (value.isEmpty()) return null
        return WiThrottleCommand.SetDirection(key, target, Direction.fromWire(value[0]))
    }

    private fun decodeSpeedStepMode(
        key: ThrottleKey,
        target: LocoId,
        payload: String,
    ): WiThrottleCommand? {
        val value = payload.substring(1)
        if (value.isEmpty()) return null
        return WiThrottleCommand.SetSpeedStepMode(key, target, SpeedStepMode.fromWire(value))
    }
}
