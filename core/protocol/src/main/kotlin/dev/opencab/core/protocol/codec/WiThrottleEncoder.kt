package dev.opencab.core.protocol.codec

import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.codec.WireSyntax.CONSIST_SEPARATOR
import dev.opencab.core.protocol.codec.WireSyntax.SEPARATOR

/** Encodes typed WiThrottle commands to exact wire lines, without a trailing newline. */
object WiThrottleEncoder {
    fun encode(command: WiThrottleCommand): String = when (command) {
        is WiThrottleCommand.SetDeviceName, is WiThrottleCommand.SetHardwareId,
        WiThrottleCommand.Heartbeat, WiThrottleCommand.EnableHeartbeatMonitoring,
        WiThrottleCommand.DisableHeartbeatMonitoring, WiThrottleCommand.Quit,
        is WiThrottleCommand.SetTrackPower,
        -> encodeSession(command)

        is WiThrottleCommand.Acquire, is WiThrottleCommand.Release, is WiThrottleCommand.SetSpeed,
        is WiThrottleCommand.SetDirection, is WiThrottleCommand.SetIdle, is WiThrottleCommand.EmergencyStop,
        is WiThrottleCommand.PressFunction, is WiThrottleCommand.ReleaseFunction,
        is WiThrottleCommand.ForceFunction, is WiThrottleCommand.SetFunctionMomentary,
        is WiThrottleCommand.SetSpeedStepMode, is WiThrottleCommand.QuerySpeed,
        is WiThrottleCommand.QueryDirection, is WiThrottleCommand.QuerySpeedStepMode,
        is WiThrottleCommand.QueryMomentaryStates, is WiThrottleCommand.SetConsistLead,
        is WiThrottleCommand.Steal,
        -> encodeThrottle(command)

        is WiThrottleCommand.ConsistAdd, is WiThrottleCommand.ConsistRemoveLoco,
        is WiThrottleCommand.ConsistSetOrder, is WiThrottleCommand.ConsistDelete,
        ->
            encodeConsist(command)
    }

    private fun encodeSession(command: WiThrottleCommand): String = when (command) {
        is WiThrottleCommand.SetDeviceName -> "N${command.name}"
        is WiThrottleCommand.SetHardwareId -> "HU${command.id}"
        WiThrottleCommand.Heartbeat -> "*"
        WiThrottleCommand.EnableHeartbeatMonitoring -> "*+"
        WiThrottleCommand.DisableHeartbeatMonitoring -> "*-"
        WiThrottleCommand.Quit -> "Q"
        is WiThrottleCommand.SetTrackPower -> "PPA${command.state.wireValue}"
        else -> error("Not a session command: $command")
    }

    private fun encodeThrottle(command: WiThrottleCommand): String = when (command) {
        is WiThrottleCommand.Acquire -> acquire(command)

        is WiThrottleCommand.Release -> release(command)

        is WiThrottleCommand.Steal ->
            "M${command.throttleKey}S${command.locoKey.toWire()}$SEPARATOR${command.locoKey.toWire()}"

        is WiThrottleCommand.SetSpeed, is WiThrottleCommand.SetDirection, is WiThrottleCommand.SetIdle,
        is WiThrottleCommand.EmergencyStop, is WiThrottleCommand.SetSpeedStepMode,
        is WiThrottleCommand.QuerySpeed, is WiThrottleCommand.QueryDirection,
        is WiThrottleCommand.QuerySpeedStepMode, is WiThrottleCommand.QueryMomentaryStates,
        is WiThrottleCommand.SetConsistLead,
        -> encodeMotion(command)

        is WiThrottleCommand.PressFunction, is WiThrottleCommand.ReleaseFunction,
        is WiThrottleCommand.ForceFunction, is WiThrottleCommand.SetFunctionMomentary,
        ->
            encodeFunctions(command)

        else -> error("Not a throttle command: $command")
    }

    private fun encodeMotion(command: WiThrottleCommand): String = when (command) {
        is WiThrottleCommand.SetSpeed -> action(command.throttleKey, command.target, "V${command.speed}")

        is WiThrottleCommand.SetDirection ->
            action(command.throttleKey, command.target, "R${command.direction.wireValue}")

        is WiThrottleCommand.SetIdle -> action(command.throttleKey, command.target, "I")

        is WiThrottleCommand.EmergencyStop -> action(command.throttleKey, command.target, "X")

        is WiThrottleCommand.SetSpeedStepMode ->
            action(command.throttleKey, command.target, "s${command.mode.wireValue}")

        is WiThrottleCommand.QuerySpeed -> action(command.throttleKey, command.target, "qV")

        is WiThrottleCommand.QueryDirection -> action(command.throttleKey, command.target, "qR")

        is WiThrottleCommand.QuerySpeedStepMode -> action(command.throttleKey, command.target, "qs")

        is WiThrottleCommand.QueryMomentaryStates -> action(command.throttleKey, command.target, "qm")

        is WiThrottleCommand.SetConsistLead ->
            action(command.throttleKey, command.target, "C${command.lead.toWire()}")

        else -> error("Not a motion command: $command")
    }

    private fun encodeFunctions(command: WiThrottleCommand): String = when (command) {
        is WiThrottleCommand.PressFunction ->
            action(command.throttleKey, command.target, "F1${command.function}")

        is WiThrottleCommand.ReleaseFunction ->
            action(command.throttleKey, command.target, "F0${command.function}")

        is WiThrottleCommand.ForceFunction ->
            action(
                command.throttleKey,
                command.target,
                "f${if (command.on) 1 else 0}${command.function}",
            )

        is WiThrottleCommand.SetFunctionMomentary ->
            action(
                command.throttleKey,
                command.target,
                "m${if (command.momentary) 1 else 0}${command.function}",
            )

        else -> error("Not a function command: $command")
    }

    private fun encodeConsist(command: WiThrottleCommand): String = when (command) {
        is WiThrottleCommand.ConsistAdd -> consistAdd(command)

        is WiThrottleCommand.ConsistRemoveLoco ->
            "RC-$SEPARATOR${command.consist.toWire()}$CONSIST_SEPARATOR${command.loco.toWire()}"

        is WiThrottleCommand.ConsistSetOrder ->
            "RCP$SEPARATOR${command.consist.toWire()}$CONSIST_SEPARATOR" +
                command.locos.joinToString(SEPARATOR) { it.toWire() }

        is WiThrottleCommand.ConsistDelete -> "RCR$SEPARATOR${command.consist.toWire()}"

        else -> error("Not a consist command: $command")
    }

    private fun acquire(command: WiThrottleCommand.Acquire): String {
        val key = command.throttleKey
        val loco = command.locoKey.toWire()
        return "M$key+$loco$SEPARATOR${command.selection.toWire()}"
    }

    private fun release(command: WiThrottleCommand.Release): String {
        val key = command.throttleKey
        val target = command.target.toWire()
        return "M$key-$target$SEPARATOR${command.action.wireValue}"
    }

    private fun consistAdd(command: WiThrottleCommand.ConsistAdd): String {
        val consist = command.consist.toWire()
        val loco = command.loco.toWire()
        return "RC+$SEPARATOR$consist$SEPARATOR${command.name}$CONSIST_SEPARATOR$loco$SEPARATOR${command.forward}"
    }

    private fun action(
        throttleKey: ThrottleKey,
        target: LocoId,
        payload: String,
    ): String = "M$throttleKey" + "A${target.toWire()}$SEPARATOR$payload"
}
