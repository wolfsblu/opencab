package dev.opencab.core.protocol

/** Direction as encoded by WiThrottle: `1` forward, `0` reverse. */
enum class Direction(
    val wireValue: Char,
) {
    REVERSE('0'),
    FORWARD('1'),
    ;

    companion object {
        fun fromWire(value: Char): Direction = if (value == '0') REVERSE else FORWARD

        fun fromBoolean(forward: Boolean): Direction = if (forward) FORWARD else REVERSE
    }
}

/** Track power state as reported by `PPA`. Only on/off can be requested. */
enum class TrackPowerState(
    val wireValue: Char,
    val canSet: Boolean,
) {
    OFF('0', true),
    ON('1', true),
    UNKNOWN('2', false),
    ;

    companion object {
        fun fromWire(value: Char): TrackPowerState? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Speed-step mode. The numeric wire values are the historical WiThrottle values; JMRI also accepts
 * a mode name, which is preserved in [Other] so an unfamiliar mode is not treated as a failure.
 */
sealed interface SpeedStepMode {
    val wireValue: String

    data object NmraDcc128 : SpeedStepMode {
        override val wireValue: String = "1"
    }

    data object NmraDcc28 : SpeedStepMode {
        override val wireValue: String = "2"
    }

    data object NmraDcc27 : SpeedStepMode {
        override val wireValue: String = "4"
    }

    data object NmraDcc14 : SpeedStepMode {
        override val wireValue: String = "8"
    }

    data object Motorola28 : SpeedStepMode {
        override val wireValue: String = "16"
    }

    data class Other(
        override val wireValue: String,
    ) : SpeedStepMode {
        init {
            require(wireValue.isNotEmpty()) { "Speed step mode must not be empty" }
            requireSingleLine(wireValue, "speed step mode")
        }
    }

    companion object {
        fun fromWire(value: String): SpeedStepMode = when (value) {
            NmraDcc128.wireValue -> NmraDcc128
            NmraDcc28.wireValue -> NmraDcc28
            NmraDcc27.wireValue -> NmraDcc27
            NmraDcc14.wireValue -> NmraDcc14
            Motorola28.wireValue -> Motorola28
            else -> Other(value)
        }
    }
}

/** Whether a removal asks the server to release or dispatch the locomotive. */
enum class RemovalAction(
    val wireValue: Char,
) {
    RELEASE('r'),
    DISPATCH('d'),
}
