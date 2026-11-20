package dev.opencab.core.protocol

/**
 * A typed client-to-server WiThrottle command.
 *
 * Constructors validate values that would produce malformed wire text (newlines, out-of-range
 * speeds or functions, non-settable power states). [dev.opencab.core.protocol.codec.WiThrottleEncoder]
 * performs the exact wire mapping.
 */
sealed interface WiThrottleCommand {
    /** `NOpenCab` */
    data class SetDeviceName(val name: String) : WiThrottleCommand {
        init {
            requireSingleLine(name, "device name")
        }
    }

    /** `HUopencab-android` */
    data class SetHardwareId(val id: String) : WiThrottleCommand {
        init {
            require(id.isNotEmpty()) { "Hardware id must not be empty" }
            requireSingleLine(id, "hardware id")
        }
    }

    /** `*` */
    data object Heartbeat : WiThrottleCommand

    /** `*+` */
    data object EnableHeartbeatMonitoring : WiThrottleCommand

    /** `*-` */
    data object DisableHeartbeatMonitoring : WiThrottleCommand

    /** `Q` */
    data object Quit : WiThrottleCommand

    /** `PPA0` or `PPA1`. */
    data class SetTrackPower(val state: TrackPowerState) : WiThrottleCommand {
        init {
            require(state.canSet) { "Track power state $state cannot be requested" }
        }
    }

    /**
     * `M0+L341<;>L341` or `M0+S3<;>ED&RGW 341`.
     *
     * [locoKey] is the key used by later commands. [selection] selects the address or roster entry.
     */
    data class Acquire(
        val throttleKey: ThrottleKey,
        val locoKey: LocoAddress,
        val selection: LocoSelection = LocoSelection.Address(locoKey),
    ) : WiThrottleCommand

    /** `M0-L341<;>r` (release), `M0-L341<;>d` (dispatch), or `M0-*<;>r` (all locos). */
    data class Release(
        val throttleKey: ThrottleKey,
        val target: LocoId,
        val action: RemovalAction = RemovalAction.RELEASE,
    ) : WiThrottleCommand

    /** `M0AL341<;>V30` - speed 0..126. */
    data class SetSpeed(
        val throttleKey: ThrottleKey,
        val target: LocoId,
        val speed: Int,
    ) : WiThrottleCommand {
        init {
            require(speed in MIN_SPEED..MAX_SPEED) { "Speed out of range: $speed" }
        }

        companion object {
            const val MIN_SPEED: Int = 0
            const val MAX_SPEED: Int = 126
        }
    }

    /** `M0AL341<;>R1` */
    data class SetDirection(
        val throttleKey: ThrottleKey,
        val target: LocoId,
        val direction: Direction,
    ) : WiThrottleCommand

    /** `M0AL341<;>I` - idle, i.e. speed zero without an emergency stop. */
    data class SetIdle(
        val throttleKey: ThrottleKey,
        val target: LocoId,
    ) : WiThrottleCommand

    /** `M0AL341<;>X` */
    data class EmergencyStop(
        val throttleKey: ThrottleKey,
        val target: LocoId,
    ) : WiThrottleCommand

    /** `M0AL341<;>F112` - function button down. */
    data class PressFunction(
        val throttleKey: ThrottleKey,
        val target: LocoId,
        val function: Int,
    ) : WiThrottleCommand {
        init {
            require(function in MIN_FUNCTION..MAX_FUNCTION) { "Function out of range: $function" }
        }
    }

    /** `M0AL341<;>F012` - function button up. */
    data class ReleaseFunction(
        val throttleKey: ThrottleKey,
        val target: LocoId,
        val function: Int,
    ) : WiThrottleCommand {
        init {
            require(function in MIN_FUNCTION..MAX_FUNCTION) { "Function out of range: $function" }
        }
    }

    /** `M0AL341<;>f112` - set a function state absolutely. */
    data class ForceFunction(
        val throttleKey: ThrottleKey,
        val target: LocoId,
        val function: Int,
        val on: Boolean,
    ) : WiThrottleCommand {
        init {
            require(function in MIN_FUNCTION..MAX_FUNCTION) { "Function out of range: $function" }
        }
    }

    /** `M0AL341<;>m112` - mark a function momentary or latching. */
    data class SetFunctionMomentary(
        val throttleKey: ThrottleKey,
        val target: LocoId,
        val function: Int,
        val momentary: Boolean,
    ) : WiThrottleCommand {
        init {
            require(function in MIN_FUNCTION..MAX_FUNCTION) { "Function out of range: $function" }
        }
    }

    /** `M0AL341<;>s1` */
    data class SetSpeedStepMode(
        val throttleKey: ThrottleKey,
        val target: LocoId,
        val mode: SpeedStepMode,
    ) : WiThrottleCommand

    /** `M0A*<;>qV` */
    data class QuerySpeed(
        val throttleKey: ThrottleKey,
        val target: LocoId,
    ) : WiThrottleCommand

    /** `M0A*<;>qR` */
    data class QueryDirection(
        val throttleKey: ThrottleKey,
        val target: LocoId,
    ) : WiThrottleCommand

    /** `M0A*<;>qs` */
    data class QuerySpeedStepMode(
        val throttleKey: ThrottleKey,
        val target: LocoId,
    ) : WiThrottleCommand

    /** `M0A*<;>qm` */
    data class QueryMomentaryStates(
        val throttleKey: ThrottleKey,
        val target: LocoId,
    ) : WiThrottleCommand

    /** `M0AL341<;>CL346` - route functions to a consist lead. */
    data class SetConsistLead(
        val throttleKey: ThrottleKey,
        val target: LocoId,
        val lead: LocoAddress,
    ) : WiThrottleCommand

    /** `M0SL341<;>L341` - confirm a steal. */
    data class Steal(
        val throttleKey: ThrottleKey,
        val locoKey: LocoAddress,
    ) : WiThrottleCommand

    /** `RC+<;>S74<;>My consist<:>L341<;>true` */
    data class ConsistAdd(
        val consist: LocoAddress,
        val name: String,
        val loco: LocoAddress,
        val forward: Boolean,
    ) : WiThrottleCommand {
        init {
            requireSingleLine(name, "consist name")
        }
    }

    /** `RC-<;>S74<:>L341` */
    data class ConsistRemoveLoco(
        val consist: LocoAddress,
        val loco: LocoAddress,
    ) : WiThrottleCommand

    /** `RCP<;>S74<:>L346<;>L3374` */
    data class ConsistSetOrder(
        val consist: LocoAddress,
        val locos: List<LocoAddress>,
    ) : WiThrottleCommand {
        init {
            require(locos.isNotEmpty()) { "Consist order must contain at least one loco" }
        }
    }

    /** `RCR<;>S74` */
    data class ConsistDelete(val consist: LocoAddress) : WiThrottleCommand

    companion object {
        const val MIN_FUNCTION: Int = 0
        const val MAX_FUNCTION: Int = 31
    }
}
