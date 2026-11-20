package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent

internal fun SessionReducer.handleHandshakeEvent(
    event: WiThrottleEvent,
    nowMillis: Long,
): List<WiThrottleCommand> = when (event) {
    is WiThrottleEvent.Version -> handleVersion(event, nowMillis)
    is WiThrottleEvent.HeartbeatInterval -> handleHeartbeatInterval(event.seconds, nowMillis)
    else -> emptyList()
}

private fun SessionReducer.handleVersion(
    event: WiThrottleEvent.Version,
    nowMillis: Long,
): List<WiThrottleCommand> {
    val duplicate =
        state.handshake == HandshakeState.Ready && state.server.protocolVersion == event.version
    if (state.handshake is HandshakeState.UnsupportedVersion || duplicate) return emptyList()
    state = state.copy(server = state.server.copy(protocolVersion = event.version))
    if (event.version < config.minimumProtocolVersion) {
        state = state.copy(handshake = HandshakeState.UnsupportedVersion(event.version))
        return emptyList()
    }
    state = state.copy(handshake = HandshakeState.Ready)
    val addresses = desiredReacquireAddresses()
    if (addresses.isEmpty()) return emptyList()
    state =
        state.copy(
            locos =
            state.locos +
                addresses.associateWith { address ->
                    state.locos.getValue(address).copy(
                        status = LocoStatus.REQUESTED,
                        throttleKey = config.throttleKey,
                    )
                },
        )
    return emit(
        addresses.map { address ->
            acquireCommand(address, state.locos.getValue(address).rosterEntryId)
        },
        nowMillis,
    )
}

private fun SessionReducer.desiredReacquireAddresses(): List<LocoAddress> {
    if (state.link != LinkState.CONNECTED) return emptyList()
    return state.locos.values
        .filter { it.desired && it.status != LocoStatus.ACQUIRED }
        .map { it.address }
}

private fun SessionReducer.handleHeartbeatInterval(
    seconds: Int,
    nowMillis: Long,
): List<WiThrottleCommand> {
    val previous = state.heartbeat
    val required = seconds > 0
    val enable = required && config.heartbeat.enabled
    val commands = mutableListOf<WiThrottleCommand>()
    if (enable && !previous.monitoringEnabled) commands += WiThrottleCommand.EnableHeartbeatMonitoring
    if (!required && previous.monitoringEnabled) commands += WiThrottleCommand.DisableHeartbeatMonitoring
    state =
        state.copy(
            heartbeat =
            HeartbeatState(
                required = required,
                intervalSeconds = seconds,
                monitoringEnabled = enable,
                lastSentAtMillis = if (enable) previous.lastSentAtMillis else null,
            ),
        )
    return emit(commands, nowMillis)
}
