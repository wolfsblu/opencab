package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.ConsistInfo
import dev.opencab.core.protocol.ConsistMember
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.WiThrottleCommand

internal fun SessionReducer.handleConsistIntent(
    intent: SessionIntent,
    nowMillis: Long,
): List<WiThrottleCommand> {
    if (!state.canControl) return emptyList()
    return when (intent) {
        is SessionIntent.SetConsistLead -> handleSetConsistLead(intent, nowMillis)
        is SessionIntent.AddToConsist -> handleAddToConsist(intent, nowMillis)
        is SessionIntent.RemoveFromConsist -> handleRemoveFromConsist(intent, nowMillis)
        is SessionIntent.SetConsistOrder -> handleSetConsistOrder(intent, nowMillis)
        is SessionIntent.DeleteConsist -> handleDeleteConsist(intent, nowMillis)
        else -> emptyList()
    }
}

private fun SessionReducer.handleSetConsistLead(
    intent: SessionIntent.SetConsistLead,
    nowMillis: Long,
): List<WiThrottleCommand> {
    controllableSlot(intent.address) ?: return emptyList()
    return emit(
        listOf(
            WiThrottleCommand.SetConsistLead(
                config.throttleKey,
                LocoId.of(intent.address),
                intent.lead,
            ),
        ),
        nowMillis,
    )
}

private fun SessionReducer.handleAddToConsist(
    intent: SessionIntent.AddToConsist,
    nowMillis: Long,
): List<WiThrottleCommand> {
    val existing = state.consists.firstOrNull { it.address == intent.consist }
    val name = intent.name.ifEmpty { existing?.id.orEmpty() }
    val member = ConsistMember(intent.loco, intent.forward)
    val members = existing?.members.orEmpty().filterNot { it.address == intent.loco } + member
    val updated = existing?.copy(id = name, members = members) ?: ConsistInfo(intent.consist, name, members)
    state = state.copy(consists = upsertConsist(updated))
    return emit(
        listOf(WiThrottleCommand.ConsistAdd(intent.consist, name, intent.loco, intent.forward)),
        nowMillis,
    )
}

private fun SessionReducer.handleRemoveFromConsist(
    intent: SessionIntent.RemoveFromConsist,
    nowMillis: Long,
): List<WiThrottleCommand> {
    val existing = state.consists.firstOrNull { it.address == intent.consist }
    if (existing != null) {
        val members = existing.members.filterNot { it.address == intent.loco }
        state = state.copy(consists = upsertConsist(existing.copy(members = members)))
    }
    return emit(
        listOf(WiThrottleCommand.ConsistRemoveLoco(intent.consist, intent.loco)),
        nowMillis,
    )
}

private fun SessionReducer.handleSetConsistOrder(
    intent: SessionIntent.SetConsistOrder,
    nowMillis: Long,
): List<WiThrottleCommand> {
    if (intent.locos.isEmpty()) return emptyList()
    val existing = state.consists.firstOrNull { it.address == intent.consist }
    val members =
        intent.locos.map { address ->
            existing?.members?.firstOrNull { it.address == address } ?: ConsistMember(address, true)
        }
    val updated = existing?.copy(members = members) ?: ConsistInfo(intent.consist, "", members)
    state = state.copy(consists = upsertConsist(updated))
    return emit(
        listOf(WiThrottleCommand.ConsistSetOrder(intent.consist, intent.locos)),
        nowMillis,
    )
}

private fun SessionReducer.handleDeleteConsist(
    intent: SessionIntent.DeleteConsist,
    nowMillis: Long,
): List<WiThrottleCommand> {
    state = state.copy(consists = state.consists.filterNot { it.address == intent.consist })
    return emit(listOf(WiThrottleCommand.ConsistDelete(intent.consist)), nowMillis)
}
