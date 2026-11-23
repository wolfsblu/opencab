package dev.opencab.core.data.throttle

import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.session.FunctionSlot
import dev.opencab.core.protocol.session.LocoSlot
import dev.opencab.core.protocol.session.LocoStatus
import dev.opencab.core.protocol.session.SessionInput
import dev.opencab.core.protocol.session.SessionIntent
import dev.opencab.core.protocol.session.SessionReducer
import dev.opencab.core.protocol.session.SessionState
import kotlin.math.roundToInt

/** Mutable engine-side state for one slot. Published only as [ThrottleSlotUiState]. */
internal data class ThrottleSlotState(
    val address: LocoAddress,
    var name: String?,
    var speedSteps: SpeedSteps,
    var desired: Boolean,
    var speed: Double,
    var acquisition: ThrottleAcquisitionState,
    var health: ThrottleHealth,
    var issue: ThrottleIssue?,
    var emergencyStop: Boolean = false,
    var acquireFailed: Boolean = false,
    var reacquireStopPending: Boolean = false,
) {
    fun canControl(): Boolean = desired &&
        health == ThrottleHealth.ONLINE &&
        acquisition == ThrottleAcquisitionState.ACQUIRED
}

internal fun LocoStatus.toAcquisitionState(): ThrottleAcquisitionState = when (this) {
    LocoStatus.IDLE -> ThrottleAcquisitionState.IDLE
    LocoStatus.REQUESTED -> ThrottleAcquisitionState.REQUESTED
    LocoStatus.ACQUIRED -> ThrottleAcquisitionState.ACQUIRED
    LocoStatus.STEAL_PENDING -> ThrottleAcquisitionState.IN_USE
    LocoStatus.STEALING -> ThrottleAcquisitionState.TAKING_CONTROL
    LocoStatus.RELEASING -> ThrottleAcquisitionState.RELEASING
    LocoStatus.LOST -> ThrottleAcquisitionState.LOST
}

/** The wire speed range is 0..126 regardless of the displayed step mode. */
internal fun Double.toWireSpeed(): Int = (coerceIn(0.0, 1.0) * MAX_WIRE_SPEED).roundToInt()

internal fun Int.toNormalizedSpeed(): Double = (this.toDouble() / MAX_WIRE_SPEED).coerceIn(0.0, 1.0)

internal const val MAX_WIRE_SPEED: Int = 126

internal fun LocoId.addresses(all: Collection<LocoAddress>): List<LocoAddress> = when (this) {
    is LocoId.Addressed -> listOf(address)
    LocoId.All -> all.toList()
}

internal fun LocoId.addressedAddress(): LocoAddress? = (this as? LocoId.Addressed)?.address

internal fun SessionReducer.applyIntent(
    intent: SessionIntent,
    atMillis: Long,
): List<WiThrottleCommand> = handle(SessionInput.Intent(intent, atMillis))

internal fun ThrottleSlotState.toUi(
    session: SessionState,
    config: ThrottleConfig,
): ThrottleSlotUiState {
    val loco = session.locos[address]
    return ThrottleSlotUiState(
        address = address,
        name = name,
        speedSteps = speedSteps,
        speed = speed.coerceIn(0.0, 1.0),
        direction = loco?.direction ?: Direction.FORWARD,
        functions = config.functionRange.map { number ->
            loco.functionUi(number, canControl())
        },
        acquisition = acquisition,
        health = health,
        desired = desired,
        emergencyStop = emergencyStop,
        issue = issue,
    )
}

private fun LocoSlot?.functionUi(
    number: Int,
    enabled: Boolean,
): ThrottleFunctionUiState {
    val function = this?.function(number) ?: FunctionSlot()
    val label = this?.functionLabels?.getOrNull(number)?.takeIf { it.isNotBlank() } ?: "F$number"
    return ThrottleFunctionUiState(
        number = number,
        label = label,
        isOn = function.isOn,
        isMomentary = function.isMomentary,
        isEnabled = enabled,
    )
}
