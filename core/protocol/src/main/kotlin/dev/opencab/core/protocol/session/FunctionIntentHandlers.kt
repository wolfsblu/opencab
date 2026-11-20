package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.WiThrottleCommand

internal fun SessionReducer.handleFunctionIntent(
    intent: SessionIntent,
    nowMillis: Long,
): List<WiThrottleCommand> = when (intent) {
    is SessionIntent.PressFunction -> handlePressFunction(intent, nowMillis)
    is SessionIntent.ReleaseFunction -> handleReleaseFunction(intent, nowMillis)
    is SessionIntent.ForceFunction -> handleForceFunction(intent, nowMillis)
    is SessionIntent.SetFunctionMomentary -> handleSetFunctionMomentary(intent, nowMillis)
    else -> emptyList()
}

private fun SessionReducer.handlePressFunction(
    intent: SessionIntent.PressFunction,
    nowMillis: Long,
): List<WiThrottleCommand> {
    if (!isValidFunction(intent.function)) return emptyList()
    val slot = controllableSlot(intent.address) ?: return emptyList()
    val current = slot.function(intent.function)
    val nextOn = if (current.isMomentary) true else !current.isOn
    applyFunctionTo(LocoId.of(intent.address), intent.function) { it.copy(isOn = nextOn) }
    return emit(
        listOf(WiThrottleCommand.PressFunction(config.throttleKey, LocoId.of(intent.address), intent.function)),
        nowMillis,
    )
}

private fun SessionReducer.handleReleaseFunction(
    intent: SessionIntent.ReleaseFunction,
    nowMillis: Long,
): List<WiThrottleCommand> {
    if (!isValidFunction(intent.function)) return emptyList()
    val slot = controllableSlot(intent.address) ?: return emptyList()
    val current = slot.function(intent.function)
    if (current.isMomentary) {
        applyFunctionTo(LocoId.of(intent.address), intent.function) { it.copy(isOn = false) }
    }
    return emit(
        listOf(
            WiThrottleCommand.ReleaseFunction(
                config.throttleKey,
                LocoId.of(intent.address),
                intent.function,
            ),
        ),
        nowMillis,
    )
}

private fun SessionReducer.handleForceFunction(
    intent: SessionIntent.ForceFunction,
    nowMillis: Long,
): List<WiThrottleCommand> {
    if (!isValidFunction(intent.function)) return emptyList()
    controllableSlot(intent.address) ?: return emptyList()
    applyFunctionTo(LocoId.of(intent.address), intent.function) { it.copy(isOn = intent.on) }
    return emit(
        listOf(
            WiThrottleCommand.ForceFunction(
                config.throttleKey,
                LocoId.of(intent.address),
                intent.function,
                intent.on,
            ),
        ),
        nowMillis,
    )
}

private fun SessionReducer.handleSetFunctionMomentary(
    intent: SessionIntent.SetFunctionMomentary,
    nowMillis: Long,
): List<WiThrottleCommand> {
    if (!isValidFunction(intent.function)) return emptyList()
    controllableSlot(intent.address) ?: return emptyList()
    applyFunctionTo(LocoId.of(intent.address), intent.function) {
        it.copy(isMomentary = intent.momentary)
    }
    return emit(
        listOf(
            WiThrottleCommand.SetFunctionMomentary(
                config.throttleKey,
                LocoId.of(intent.address),
                intent.function,
                intent.momentary,
            ),
        ),
        nowMillis,
    )
}

private fun isValidFunction(number: Int): Boolean {
    val range = WiThrottleCommand.MIN_FUNCTION..WiThrottleCommand.MAX_FUNCTION
    return number in range
}
