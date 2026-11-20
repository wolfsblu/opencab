package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.WiThrottleCommand

internal fun SessionReducer.handleMotionIntent(
    intent: SessionIntent,
    nowMillis: Long,
): List<WiThrottleCommand> = when (intent) {
    is SessionIntent.SetSpeed -> handleSetSpeed(intent, nowMillis)
    is SessionIntent.SetDirection -> handleSetDirection(intent, nowMillis)
    is SessionIntent.EmergencyStop -> handleEmergencyStop(intent, nowMillis)
    is SessionIntent.SetSpeedStepMode -> handleSetSpeedStepMode(intent, nowMillis)
    is SessionIntent.QuerySpeed -> query(intent.address, nowMillis) { querySpeed(it) }
    is SessionIntent.QueryDirection -> query(intent.address, nowMillis) { queryDirection(it) }
    is SessionIntent.QuerySpeedStepMode -> query(intent.address, nowMillis) { querySpeedStepMode(it) }
    is SessionIntent.QueryMomentaryStates -> query(intent.address, nowMillis) { queryMomentaryStates(it) }
    else -> emptyList()
}

private fun SessionReducer.handleSetSpeed(
    intent: SessionIntent.SetSpeed,
    nowMillis: Long,
): List<WiThrottleCommand> {
    if (intent.speed !in WiThrottleCommand.SetSpeed.MIN_SPEED..WiThrottleCommand.SetSpeed.MAX_SPEED) {
        return emptyList()
    }
    val slot = controllableSlot(intent.address) ?: return emptyList()
    val isDuplicate =
        intent.speed != 0 &&
            slot.speed == intent.speed &&
            !slot.emergencyStop &&
            slot.speedSource != SpeedSource.UNKNOWN
    if (isDuplicate) return emptyList()
    updateLoco(intent.address) {
        it.copy(speed = intent.speed, emergencyStop = false, speedSource = SpeedSource.COMMANDED)
    }
    return emit(
        listOf(WiThrottleCommand.SetSpeed(config.throttleKey, LocoId.of(intent.address), intent.speed)),
        nowMillis,
    )
}

private fun SessionReducer.handleSetDirection(
    intent: SessionIntent.SetDirection,
    nowMillis: Long,
): List<WiThrottleCommand> {
    val slot = controllableSlot(intent.address) ?: return emptyList()
    if (slot.direction == intent.direction) return emptyList()
    updateLoco(intent.address) { it.copy(direction = intent.direction) }
    return emit(
        listOf(
            WiThrottleCommand.SetDirection(
                config.throttleKey,
                LocoId.of(intent.address),
                intent.direction,
            ),
        ),
        nowMillis,
    )
}

private fun SessionReducer.handleEmergencyStop(
    intent: SessionIntent.EmergencyStop,
    nowMillis: Long,
): List<WiThrottleCommand> {
    controllableSlot(intent.address) ?: return emptyList()
    updateLoco(intent.address) {
        it.copy(speed = 0, emergencyStop = true, speedSource = SpeedSource.COMMANDED)
    }
    return emit(
        listOf(WiThrottleCommand.EmergencyStop(config.throttleKey, LocoId.of(intent.address))),
        nowMillis,
    )
}

private fun SessionReducer.handleSetSpeedStepMode(
    intent: SessionIntent.SetSpeedStepMode,
    nowMillis: Long,
): List<WiThrottleCommand> {
    val slot = controllableSlot(intent.address) ?: return emptyList()
    if (slot.speedStepMode == intent.mode) return emptyList()
    updateLoco(intent.address) { it.copy(speedStepMode = intent.mode) }
    return emit(
        listOf(
            WiThrottleCommand.SetSpeedStepMode(
                config.throttleKey,
                LocoId.of(intent.address),
                intent.mode,
            ),
        ),
        nowMillis,
    )
}

private fun SessionReducer.query(
    address: LocoAddress,
    nowMillis: Long,
    command: (LocoId) -> WiThrottleCommand,
): List<WiThrottleCommand> {
    controllableSlot(address) ?: return emptyList()
    return emit(listOf(command(LocoId.of(address))), nowMillis)
}

private fun SessionReducer.querySpeed(target: LocoId): WiThrottleCommand {
    val key = config.throttleKey
    return WiThrottleCommand.QuerySpeed(key, target)
}

private fun SessionReducer.queryDirection(target: LocoId): WiThrottleCommand {
    val key = config.throttleKey
    return WiThrottleCommand.QueryDirection(key, target)
}

private fun SessionReducer.querySpeedStepMode(target: LocoId): WiThrottleCommand {
    val key = config.throttleKey
    return WiThrottleCommand.QuerySpeedStepMode(key, target)
}

private fun SessionReducer.queryMomentaryStates(target: LocoId): WiThrottleCommand {
    val key = config.throttleKey
    return WiThrottleCommand.QueryMomentaryStates(key, target)
}
