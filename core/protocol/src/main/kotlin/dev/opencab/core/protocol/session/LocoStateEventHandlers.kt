package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent

internal fun SessionReducer.handleLocoStateEvent(event: WiThrottleEvent): List<WiThrottleCommand> {
    when (event) {
        is WiThrottleEvent.SpeedChanged ->
            applyTo(event.locoId) {
                it.copy(
                    speed = event.speed,
                    emergencyStop = event.emergencyStop,
                    speedSource = SpeedSource.LIVE,
                )
            }

        is WiThrottleEvent.DirectionChanged ->
            applyTo(event.locoId) { it.copy(direction = event.direction) }

        is WiThrottleEvent.SpeedStepModeChanged ->
            applyTo(event.locoId) { it.copy(speedStepMode = event.mode) }

        is WiThrottleEvent.FunctionStateChanged ->
            applyFunctionTo(event.locoId, event.function) { it.copy(isOn = event.isOn) }

        is WiThrottleEvent.FunctionMomentaryChanged ->
            applyFunctionTo(event.locoId, event.function) { it.copy(isMomentary = event.isMomentary) }

        else -> Unit
    }
    return emptyList()
}
