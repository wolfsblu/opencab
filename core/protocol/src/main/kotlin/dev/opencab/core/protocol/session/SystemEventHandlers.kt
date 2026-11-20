package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.ConsistInfo
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent

internal fun SessionReducer.handleSystemEvent(event: WiThrottleEvent): List<WiThrottleCommand> {
    when (event) {
        is WiThrottleEvent.TrackPower -> state = state.copy(power = event.state)

        is WiThrottleEvent.ServerType -> state = state.copy(server = state.server.copy(type = event.type))

        is WiThrottleEvent.ServerDescription ->
            state = state.copy(server = state.server.copy(description = event.description))

        is WiThrottleEvent.Alert -> state = state.copy(lastAlert = event.message)

        is WiThrottleEvent.Info -> state = state.copy(lastInfo = event.message)

        is WiThrottleEvent.WebPort ->
            state = state.copy(server = state.server.copy(webPort = event.port))

        is WiThrottleEvent.RosterList -> state = state.copy(roster = event.entries)

        is WiThrottleEvent.ConsistListHeader -> state = state.copy(consistCount = event.declaredCount)

        is WiThrottleEvent.ConsistEntry -> state = state.copy(consists = upsertConsist(event.consist))

        else -> Unit
    }
    return emptyList()
}

internal fun SessionReducer.upsertConsist(consist: ConsistInfo): List<ConsistInfo> {
    val index = state.consists.indexOfFirst { it.address == consist.address }
    return if (index < 0) {
        state.consists + consist
    } else {
        state.consists.toMutableList().also { it[index] = consist }
    }
}
