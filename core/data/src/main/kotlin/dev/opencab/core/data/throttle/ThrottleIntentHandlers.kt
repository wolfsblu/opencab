package dev.opencab.core.data.throttle

import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.LocoSelection
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.WiThrottleCommand
import dev.opencab.core.protocol.session.FunctionSlot
import dev.opencab.core.protocol.session.LocoStatus
import dev.opencab.core.protocol.session.SessionIntent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal suspend fun ThrottleController.handleIntent(intent: ThrottleIntent) {
    when (intent) {
        is ThrottleIntent.Link -> handleLink(intent.client)
        ThrottleIntent.Unlink -> handleUnlink()
        is ThrottleIntent.Acquire -> handleAcquireIntent(intent)
        is ThrottleIntent.Release -> handleReleaseIntent(intent)
        is ThrottleIntent.SetSpeed -> handleSetSpeedIntent(intent)
        is ThrottleIntent.Nudge -> handleNudgeIntent(intent)
        is ThrottleIntent.SetDirection -> handleSetDirectionIntent(intent)
        is ThrottleIntent.SetSpeedSteps -> handleSetSpeedStepsIntent(intent)
        is ThrottleIntent.ToggleFunction -> handleToggleFunctionIntent(intent)
        is ThrottleIntent.SetFunction -> handleSetFunctionIntent(intent)
        is ThrottleIntent.EmergencyStop -> stopSlot(intent.address, emergency = true)
        is ThrottleIntent.Stop -> stopSlot(intent.address, emergency = false)
        is ThrottleIntent.TakeControl -> handleTakeControlIntent(intent)
    }
}

private suspend fun ThrottleController.handleAcquireIntent(intent: ThrottleIntent.Acquire) {
    val wasFailed = slots[intent.address]?.acquireFailed == true
    val existing = slots[intent.address]
    if (existing == null) {
        slots[intent.address] = ThrottleSlotState(
            address = intent.address,
            name = intent.name?.takeIf { it.isNotBlank() },
            speedSteps = intent.speedSteps,
            desired = true,
            speed = 0.0,
            acquisition = ThrottleAcquisitionState.IDLE,
            health = if (linkState == ThrottleLinkState.CONNECTED) {
                ThrottleHealth.ONLINE
            } else {
                ThrottleHealth.OFFLINE
            },
            issue = null,
        )
    } else {
        intent.name?.takeIf { it.isNotBlank() }?.let { existing.name = it }
        existing.speedSteps = intent.speedSteps
        existing.desired = true
        if (existing.acquisition != ThrottleAcquisitionState.IN_USE &&
            existing.acquisition != ThrottleAcquisitionState.TAKING_CONTROL
        ) {
            existing.issue = null
        }
        existing.acquireFailed = false
    }

    val slot = slots.getValue(intent.address)
    if (slot.acquisition == ThrottleAcquisitionState.ACQUIRED) {
        syncSlots()
        return
    }

    val commands = if (shouldResendAcquire(intent.address, wasFailed)) {
        listOf(acquireCommand(intent.address, intent.rosterEntryId))
    } else {
        reducer.applyIntent(
            SessionIntent.Acquire(intent.address, intent.name, intent.rosterEntryId),
            nowMillis(),
        )
    }
    val sent = sendCommands(commands, intent.address)
    if (commands.any { it is WiThrottleCommand.Acquire }) {
        if (sent) {
            slot.acquisition = ThrottleAcquisitionState.REQUESTED
            startAcquireTimeout(intent.address)
        } else {
            slot.acquireFailed = true
            slot.acquisition = ThrottleAcquisitionState.FAILED
        }
    }
    syncSlots()
}

private fun ThrottleController.shouldResendAcquire(
    address: LocoAddress,
    wasFailed: Boolean,
): Boolean = wasFailed &&
    linkState == ThrottleLinkState.CONNECTED &&
    reducer.state.locos[address]?.status == LocoStatus.REQUESTED

private fun acquireCommand(
    address: LocoAddress,
    rosterEntryId: String?,
): WiThrottleCommand.Acquire = WiThrottleCommand.Acquire(
    throttleKey = ThrottleKey.DEFAULT,
    locoKey = address,
    selection = rosterEntryId?.let(LocoSelection::RosterEntry) ?: LocoSelection.Address(address),
)

private suspend fun ThrottleController.handleReleaseIntent(intent: ThrottleIntent.Release) {
    val slot = slots[intent.address] ?: return
    slot.desired = false
    pendingSpeeds.remove(intent.address)
    cancelAcquireTimeout(intent.address)
    cancelMomentaryJobs(intent.address)
    val commands = reducer.applyIntent(SessionIntent.Release(intent.address), nowMillis())
    sendCommands(commands, intent.address)
    slot.acquisition = ThrottleAcquisitionState.RELEASING
    syncSlots()
}

private suspend fun ThrottleController.handleSetSpeedIntent(intent: ThrottleIntent.SetSpeed) {
    val normalized = intent.speed.coerceIn(0.0, 1.0)
    if (normalized == 0.0) {
        stopSlot(intent.address, emergency = false)
        return
    }
    val slot = slots[intent.address] ?: return
    if (!slot.canControl()) return
    slot.speed = normalized
    slot.emergencyStop = false
    pendingSpeeds[intent.address] = normalized.toWireSpeed()
}

private suspend fun ThrottleController.handleNudgeIntent(intent: ThrottleIntent.Nudge) {
    val slot = slots[intent.address] ?: return
    if (!slot.canControl()) return
    val stepCount = slot.speedSteps.steps
    val currentStep = (slot.speed * stepCount).roundToInt()
    val nextStep = (currentStep + intent.steps).coerceIn(0, stepCount)
    handleSetSpeedIntent(
        ThrottleIntent.SetSpeed(intent.address, nextStep.toDouble() / stepCount),
    )
}

private suspend fun ThrottleController.handleSetDirectionIntent(
    intent: ThrottleIntent.SetDirection,
) {
    val slot = slots[intent.address] ?: return
    if (!slot.canControl()) return
    val commands = reducer.applyIntent(
        SessionIntent.SetDirection(intent.address, intent.direction),
        nowMillis(),
    )
    sendCommands(commands, intent.address)
    syncSlots()
}

private suspend fun ThrottleController.handleSetSpeedStepsIntent(
    intent: ThrottleIntent.SetSpeedSteps,
) {
    val slot = slots[intent.address] ?: return
    slot.speedSteps = intent.speedSteps
    if (!slot.canControl()) {
        syncSlots()
        return
    }
    val commands = reducer.applyIntent(
        SessionIntent.SetSpeedStepMode(intent.address, intent.speedSteps.protocolMode),
        nowMillis(),
    )
    sendCommands(commands, intent.address)
    syncSlots()
}

private suspend fun ThrottleController.handleToggleFunctionIntent(
    intent: ThrottleIntent.ToggleFunction,
) {
    if (intent.function !in config.functionRange) return
    val slot = slots[intent.address] ?: return
    if (!slot.canControl()) return
    val current = reducer.state.locos[intent.address]?.function(intent.function) ?: FunctionSlot()
    if (current.isMomentary) {
        if (current.isOn) {
            handleSetFunctionIntent(
                ThrottleIntent.SetFunction(intent.address, intent.function, false),
            )
        } else {
            handleSetFunctionIntent(
                ThrottleIntent.SetFunction(intent.address, intent.function, true),
            )
            scheduleMomentaryRelease(intent.address, intent.function)
        }
    } else {
        handleSetFunctionIntent(
            ThrottleIntent.SetFunction(intent.address, intent.function, !current.isOn),
        )
    }
}

private suspend fun ThrottleController.handleSetFunctionIntent(
    intent: ThrottleIntent.SetFunction,
) {
    if (intent.function !in config.functionRange) return
    val slot = slots[intent.address] ?: return
    if (!slot.canControl()) return
    cancelMomentaryJob(intent.address, intent.function)
    val commands = reducer.applyIntent(
        SessionIntent.ForceFunction(intent.address, intent.function, intent.on),
        nowMillis(),
    )
    sendCommands(commands, intent.address)
    syncSlots()
}

private suspend fun ThrottleController.handleTakeControlIntent(
    intent: ThrottleIntent.TakeControl,
) {
    val slot = slots[intent.address] ?: return
    if (slot.acquisition != ThrottleAcquisitionState.IN_USE &&
        slot.acquisition != ThrottleAcquisitionState.TAKING_CONTROL
    ) {
        return
    }
    slot.issue = null
    slot.acquireFailed = false
    val commands = reducer.applyIntent(SessionIntent.ConfirmSteal(intent.address), nowMillis())
    sendCommands(commands, intent.address)
    syncSlots()
}

internal suspend fun ThrottleController.stopSlot(
    address: LocoAddress,
    emergency: Boolean,
) {
    val slot = slots[address] ?: return
    if (!slot.canControl()) {
        if (emergency) slot.emergencyStop = true
        return
    }
    pendingSpeeds.remove(address)
    slot.speed = 0.0
    slot.emergencyStop = emergency
    val commands = if (emergency) {
        reducer.applyIntent(SessionIntent.EmergencyStop(address), nowMillis())
    } else {
        reducer.applyIntent(SessionIntent.SetSpeed(address, 0), nowMillis())
    }
    sendCommands(commands, address)
    syncSlots()
}

internal fun ThrottleController.handleAcquireTimeout(address: LocoAddress) {
    acquireJobs.remove(address)
    val slot = slots[address] ?: return
    if (slot.acquisition != ThrottleAcquisitionState.REQUESTED) return
    slot.acquireFailed = true
    slot.acquisition = ThrottleAcquisitionState.FAILED
    slot.issue = ThrottleIssue.AcquireTimedOut(address)
}

internal fun ThrottleController.startAcquireTimeout(address: LocoAddress) {
    cancelAcquireTimeout(address)
    val job = scope.launch {
        delay(config.acquireTimeoutMillis)
        inputs.send(ThrottleInput.AcquireTimeout(address))
    }
    acquireJobs[address] = job
}

internal fun ThrottleController.cancelAcquireTimeout(address: LocoAddress) {
    acquireJobs.remove(address)?.cancel()
}

internal fun ThrottleController.startTimeoutsForRequestedSlots() {
    slots.values
        .filter {
            it.desired &&
                it.acquisition == ThrottleAcquisitionState.REQUESTED &&
                !it.acquireFailed
        }
        .forEach { startAcquireTimeout(it.address) }
}

private fun ThrottleController.scheduleMomentaryRelease(
    address: LocoAddress,
    function: Int,
) {
    cancelMomentaryJob(address, function)
    val key = ThrottleController.SlotFunction(address, function)
    val job = scope.launch {
        delay(config.momentaryPulseMillis)
        momentaryJobs.remove(key)
        dispatch(ThrottleIntent.SetFunction(address, function, false))
    }
    momentaryJobs[key] = job
}

private fun ThrottleController.cancelMomentaryJob(
    address: LocoAddress,
    function: Int,
) {
    momentaryJobs.remove(ThrottleController.SlotFunction(address, function))?.cancel()
}

internal fun ThrottleController.cancelMomentaryJobs(address: LocoAddress) {
    val matching = momentaryJobs.keys.filter { it.address == address }
    matching.forEach { momentaryJobs.remove(it)?.cancel() }
}
