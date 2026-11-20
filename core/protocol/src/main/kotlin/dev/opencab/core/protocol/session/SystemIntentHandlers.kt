package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.WiThrottleCommand

internal fun SessionReducer.handleSystemIntent(
    intent: SessionIntent,
    nowMillis: Long,
): List<WiThrottleCommand> = when (intent) {
    is SessionIntent.SetTrackPower -> handleSetTrackPower(intent, nowMillis)
    is SessionIntent.SetHeartbeatEnabled -> handleSetHeartbeatEnabled(intent, nowMillis)
    SessionIntent.Quit -> emit(listOf(WiThrottleCommand.Quit), nowMillis)
    else -> emptyList()
}

private fun SessionReducer.handleSetTrackPower(
    intent: SessionIntent.SetTrackPower,
    nowMillis: Long,
): List<WiThrottleCommand> {
    if (!config.allowTrackPowerControl || !state.canControl || !intent.state.canSet) {
        return emptyList()
    }
    state = state.copy(power = intent.state)
    return emit(listOf(WiThrottleCommand.SetTrackPower(intent.state)), nowMillis)
}

private fun SessionReducer.handleSetHeartbeatEnabled(
    intent: SessionIntent.SetHeartbeatEnabled,
    nowMillis: Long,
): List<WiThrottleCommand> {
    if (intent.enabled == state.heartbeat.monitoringEnabled) return emptyList()
    if (state.link != LinkState.CONNECTED) return emptyList()
    state = state.copy(heartbeat = state.heartbeat.copy(monitoringEnabled = intent.enabled))
    val command =
        if (intent.enabled) {
            WiThrottleCommand.EnableHeartbeatMonitoring
        } else {
            WiThrottleCommand.DisableHeartbeatMonitoring
        }
    return emit(listOf(command), nowMillis)
}
