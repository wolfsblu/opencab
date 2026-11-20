package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.WiThrottleCommand

internal fun SessionReducer.handleAcquisitionIntent(
    intent: SessionIntent,
    nowMillis: Long,
): List<WiThrottleCommand> = when (intent) {
    is SessionIntent.Acquire -> handleAcquire(intent, nowMillis)
    is SessionIntent.Release -> handleRelease(intent, nowMillis)
    is SessionIntent.ConfirmSteal -> handleConfirmSteal(intent, nowMillis)
    else -> emptyList()
}

private fun SessionReducer.handleAcquire(
    intent: SessionIntent.Acquire,
    nowMillis: Long,
): List<WiThrottleCommand> {
    val existing = state.locos[intent.address]
    if (existing != null && existing.status in ACTIVE_ACQUISITION_STATUSES) {
        val needsUpdate = intent.name != null || intent.rosterEntryId != null || !existing.desired
        if (needsUpdate) {
            val updated =
                existing.copy(
                    name = intent.name ?: existing.name,
                    rosterEntryId = intent.rosterEntryId ?: existing.rosterEntryId,
                    desired = true,
                )
            state = state.copy(locos = state.locos + (intent.address to updated))
        }
        return emptyList()
    }
    val canSend = state.canControl
    val base = existing ?: LocoSlot(address = intent.address, name = rosterName(intent.address))
    val slot =
        base.copy(
            name = intent.name ?: base.name,
            rosterEntryId = intent.rosterEntryId ?: base.rosterEntryId,
            desired = true,
            status = if (canSend) LocoStatus.REQUESTED else LocoStatus.IDLE,
            throttleKey = config.throttleKey,
        )
    state = state.copy(locos = state.locos + (intent.address to slot))
    if (!canSend) return emptyList()
    return emit(listOf(acquireCommand(intent.address, slot.rosterEntryId)), nowMillis)
}

private fun SessionReducer.handleRelease(
    intent: SessionIntent.Release,
    nowMillis: Long,
): List<WiThrottleCommand> {
    val slot = state.locos[intent.address] ?: return emptyList()
    val cancellable = slot.status == LocoStatus.STEAL_PENDING || slot.status == LocoStatus.STEALING
    if (slot.status == LocoStatus.RELEASING || cancellable) {
        if (cancellable) state = state.copy(locos = state.locos - intent.address)
        return emptyList()
    }
    val canSend =
        state.canControl && (slot.status == LocoStatus.ACQUIRED || slot.status == LocoStatus.REQUESTED)
    if (!canSend) {
        state = state.copy(locos = state.locos - intent.address)
        return emptyList()
    }
    val commands = mutableListOf<WiThrottleCommand>()
    if (slot.status == LocoStatus.ACQUIRED) {
        commands += WiThrottleCommand.SetSpeed(config.throttleKey, LocoId.of(intent.address), 0)
    }
    commands += WiThrottleCommand.Release(config.throttleKey, LocoId.of(intent.address))
    state =
        state.copy(
            locos =
            state.locos +
                (intent.address to slot.copy(desired = false, status = LocoStatus.RELEASING)),
        )
    return emit(commands, nowMillis)
}

private fun SessionReducer.handleConfirmSteal(
    intent: SessionIntent.ConfirmSteal,
    nowMillis: Long,
): List<WiThrottleCommand> {
    val slot = state.locos[intent.address] ?: return emptyList()
    if (!state.canControl || slot.status != LocoStatus.STEAL_PENDING) return emptyList()
    state =
        state.copy(
            locos =
            state.locos +
                (intent.address to slot.copy(desired = true, status = LocoStatus.STEALING)),
        )
    return emit(listOf(WiThrottleCommand.Steal(config.throttleKey, intent.address)), nowMillis)
}

private val ACTIVE_ACQUISITION_STATUSES: Set<LocoStatus> =
    setOf(
        LocoStatus.REQUESTED,
        LocoStatus.ACQUIRED,
        LocoStatus.STEAL_PENDING,
        LocoStatus.STEALING,
        LocoStatus.RELEASING,
    )
