package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoId
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.WiThrottleEvent

internal fun SessionReducer.handleAcquisitionEvent(event: WiThrottleEvent): List<WiThrottleCommand> {
    when (event) {
        is WiThrottleEvent.LocoAdded -> handleLocoAdded(event)
        is WiThrottleEvent.LocoRemoved -> handleLocoRemoved(event)
        is WiThrottleEvent.StealRequested -> handleStealRequested(event)
        is WiThrottleEvent.FunctionLabels -> handleFunctionLabels(event)
        else -> Unit
    }
    return emptyList()
}

private fun SessionReducer.handleLocoAdded(event: WiThrottleEvent.LocoAdded) {
    val address = event.locoId.addressedAddress() ?: return
    val existing = state.locos[address]
    val slot =
        if (existing == null) {
            LocoSlot(
                address = address,
                name = rosterName(address),
                rosterEntryId = rosterName(address),
                status = LocoStatus.ACQUIRED,
                throttleKey = event.throttleKey,
            )
        } else {
            existing.copy(status = LocoStatus.ACQUIRED, throttleKey = event.throttleKey)
        }
    state = state.copy(locos = state.locos + (address to slot))
}

private fun SessionReducer.handleLocoRemoved(event: WiThrottleEvent.LocoRemoved) {
    when (val locoId = event.locoId) {
        is LocoId.All ->
            state.locos.values
                .filter { it.throttleKey == event.throttleKey }
                .forEach { removeOrMark(it.address) }

        is LocoId.Addressed -> removeOrMark(locoId.address)
    }
}

private fun SessionReducer.removeOrMark(address: LocoAddress) {
    val slot = state.locos[address] ?: return
    if (slot.status == LocoStatus.RELEASING || !slot.desired) {
        state = state.copy(locos = state.locos - address)
    } else {
        state = state.copy(
            locos = state.locos + (address to slot.copy(status = LocoStatus.LOST)),
        )
    }
}

private fun SessionReducer.handleStealRequested(event: WiThrottleEvent.StealRequested) {
    val address = event.locoId.addressedAddress() ?: return
    val existing = state.locos[address]
    if (existing?.status == LocoStatus.ACQUIRED) return
    val base = existing ?: LocoSlot(address = address, name = rosterName(address))
    state = state.copy(
        locos =
        state.locos +
            (
                address to base.copy(
                    status = LocoStatus.STEAL_PENDING,
                    desired = true,
                    throttleKey = event.throttleKey,
                )
                ),
    )
}

private fun SessionReducer.handleFunctionLabels(event: WiThrottleEvent.FunctionLabels) {
    val address = event.locoId.addressedAddress() ?: return
    updateLoco(address) { it.copy(functionLabels = event.labels) }
}

private fun LocoId.addressedAddress(): LocoAddress? = (this as? LocoId.Addressed)?.address
