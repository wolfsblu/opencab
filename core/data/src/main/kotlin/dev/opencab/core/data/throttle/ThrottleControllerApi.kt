package dev.opencab.core.data.throttle

import dev.opencab.core.data.connection.DccClient
import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress

/** Attaches the engine to a transport. A previous link is detached first. */
fun ThrottleController.link(client: DccClient) {
    dispatch(ThrottleIntent.Link(client))
}

/** Detaches from the transport without releasing desired slots. */
fun ThrottleController.unlink() {
    dispatch(ThrottleIntent.Unlink)
}

fun ThrottleController.acquire(
    address: LocoAddress,
    name: String? = null,
    speedSteps: SpeedSteps = SpeedSteps.ONE_HUNDRED_TWENTY_EIGHT,
    rosterEntryId: String? = null,
) {
    dispatch(ThrottleIntent.Acquire(address, name, speedSteps, rosterEntryId))
}

fun ThrottleController.release(address: LocoAddress) {
    dispatch(ThrottleIntent.Release(address))
}

fun ThrottleController.setSpeed(address: LocoAddress, speed: Double) {
    dispatch(ThrottleIntent.SetSpeed(address, speed))
}

fun ThrottleController.nudge(address: LocoAddress, steps: Int) {
    dispatch(ThrottleIntent.Nudge(address, steps))
}

fun ThrottleController.setDirection(address: LocoAddress, direction: Direction) {
    dispatch(ThrottleIntent.SetDirection(address, direction))
}

fun ThrottleController.setSpeedSteps(address: LocoAddress, speedSteps: SpeedSteps) {
    dispatch(ThrottleIntent.SetSpeedSteps(address, speedSteps))
}

fun ThrottleController.toggleFunction(address: LocoAddress, function: Int) {
    dispatch(ThrottleIntent.ToggleFunction(address, function))
}

fun ThrottleController.setFunction(address: LocoAddress, function: Int, on: Boolean) {
    dispatch(ThrottleIntent.SetFunction(address, function, on))
}

fun ThrottleController.emergencyStop(address: LocoAddress) {
    dispatch(ThrottleIntent.EmergencyStop(address))
}

fun ThrottleController.stop(address: LocoAddress) {
    dispatch(ThrottleIntent.Stop(address))
}

fun ThrottleController.takeControl(address: LocoAddress) {
    dispatch(ThrottleIntent.TakeControl(address))
}
