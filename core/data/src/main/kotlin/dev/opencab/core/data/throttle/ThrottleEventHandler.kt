package dev.opencab.core.data.throttle

import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.WiThrottleEvent
import dev.opencab.core.protocol.session.SessionInput
import dev.opencab.core.protocol.session.SessionIntent

internal suspend fun ThrottleController.handleServerEvent(event: WiThrottleEvent) {
    val commands = reducer.handle(SessionInput.Event(event, nowMillis()))
    sendCommands(commands, event.addressedAddress())

    when (event) {
        is WiThrottleEvent.LocoAdded -> handleLocoAdded(event)
        is WiThrottleEvent.LocoRemoved -> handleLocoRemoved(event)
        is WiThrottleEvent.StealRequested -> handleStealRequested(event)
        is WiThrottleEvent.SpeedChanged -> handleSpeedChanged(event)
        is WiThrottleEvent.SpeedStepModeChanged -> handleSpeedStepModeChanged(event)
        is WiThrottleEvent.Alert -> lastAlert = event.message
        else -> Unit
    }

    syncSlots()
    startTimeoutsForRequestedSlots()
    publish()
}

private suspend fun ThrottleController.handleLocoAdded(event: WiThrottleEvent.LocoAdded) {
    for (address in event.locoId.addresses(slots.keys)) {
        val slot = slots[address] ?: continue
        cancelAcquireTimeout(address)
        slot.acquireFailed = false
        slot.issue = null
        if (slot.reacquireStopPending) {
            slot.reacquireStopPending = false
            slot.speed = 0.0
            slot.emergencyStop = false
            val commands = reducer.applyIntent(SessionIntent.SetSpeed(address, 0), nowMillis())
            sendCommands(commands, address)
        }
    }
}

private fun ThrottleController.handleLocoRemoved(event: WiThrottleEvent.LocoRemoved) {
    for (address in event.locoId.addresses(slots.keys)) {
        pendingSpeeds.remove(address)
        cancelAcquireTimeout(address)
        cancelMomentaryJobs(address)
        slots[address]?.reacquireStopPending = false
    }
}

private fun ThrottleController.handleStealRequested(event: WiThrottleEvent.StealRequested) {
    for (address in event.locoId.addresses(slots.keys)) {
        val slot = slots[address] ?: continue
        cancelAcquireTimeout(address)
        slot.acquireFailed = false
        slot.issue = ThrottleIssue.InUse(address)
    }
}

private fun ThrottleController.handleSpeedChanged(event: WiThrottleEvent.SpeedChanged) {
    for (address in event.locoId.addresses(slots.keys)) {
        val slot = slots[address] ?: continue
        if (event.emergencyStop) {
            pendingSpeeds.remove(address)
            slot.speed = 0.0
            slot.emergencyStop = true
        } else if (address !in pendingSpeeds) {
            slot.speed = event.speed.toNormalizedSpeed()
            slot.emergencyStop = false
        }
    }
}

private fun ThrottleController.handleSpeedStepModeChanged(
    event: WiThrottleEvent.SpeedStepModeChanged,
) {
    for (address in event.locoId.addresses(slots.keys)) {
        slots[address]?.speedSteps = SpeedSteps.fromProtocol(event.mode)
    }
}

private fun WiThrottleEvent.addressedAddress(): LocoAddress? = when (this) {
    is WiThrottleEvent.LocoAdded -> locoId.addressedAddress()
    is WiThrottleEvent.LocoRemoved -> locoId.addressedAddress()
    is WiThrottleEvent.StealRequested -> locoId.addressedAddress()
    is WiThrottleEvent.FunctionLabels -> locoId.addressedAddress()
    is WiThrottleEvent.SpeedChanged -> locoId.addressedAddress()
    is WiThrottleEvent.DirectionChanged -> locoId.addressedAddress()
    is WiThrottleEvent.SpeedStepModeChanged -> locoId.addressedAddress()
    is WiThrottleEvent.FunctionStateChanged -> locoId.addressedAddress()
    is WiThrottleEvent.FunctionMomentaryChanged -> locoId.addressedAddress()
    else -> null
}
