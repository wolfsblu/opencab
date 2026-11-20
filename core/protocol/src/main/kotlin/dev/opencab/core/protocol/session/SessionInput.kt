package dev.opencab.core.protocol.session

import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.ProtocolVersion
import dev.opencab.core.protocol.SpeedStepMode
import dev.opencab.core.protocol.ThrottleKey
import dev.opencab.core.protocol.TrackPowerState
import dev.opencab.core.protocol.WiThrottleEvent

/** Deterministic heartbeat policy. No clock or timer is created by the reducer. */
data class HeartbeatPolicy(
    /** Whether to turn server-side monitoring on when the server advertises an interval. */
    val enabled: Boolean = true,
    /** Send when this fraction of the server interval has elapsed. */
    val sendAtFractionOfInterval: Double = 0.5,
    /** Never send heartbeats faster than this, even if the server interval is tiny. */
    val minimumIntervalMillis: Long = 1_000,
) {
    init {
        require(sendAtFractionOfInterval > 0.0 && sendAtFractionOfInterval <= 1.0) {
            "Heartbeat fraction must be in (0, 1]"
        }
        require(minimumIntervalMillis > 0) { "Heartbeat minimum interval must be positive" }
    }

    fun sendIntervalMillis(intervalSeconds: Int): Long = maxOf(
        minimumIntervalMillis,
        (intervalSeconds * 1_000.0 * sendAtFractionOfInterval).toLong(),
    )
}

/** Configuration for [SessionReducer]. */
data class SessionConfig(
    val deviceName: String = "OpenCab",
    val hardwareId: String = "opencab",
    val throttleKey: ThrottleKey = ThrottleKey.DEFAULT,
    val minimumProtocolVersion: ProtocolVersion = ProtocolVersion.V2_0,
    val heartbeat: HeartbeatPolicy = HeartbeatPolicy(),
    /** v1 treats track power as read-only; opt in only when write support is verified. */
    val allowTrackPowerControl: Boolean = false,
) {
    init {
        require(deviceName.isNotEmpty()) { "Device name must not be empty" }
        require(hardwareId.isNotEmpty()) { "Hardware id must not be empty" }
    }
}

/** A user or higher-layer request. */
sealed interface SessionIntent {
    data class Acquire(
        val address: LocoAddress,
        val name: String? = null,
        val rosterEntryId: String? = null,
    ) : SessionIntent

    data class Release(val address: LocoAddress) : SessionIntent

    data class ConfirmSteal(val address: LocoAddress) : SessionIntent

    data class SetSpeed(
        val address: LocoAddress,
        val speed: Int,
    ) : SessionIntent

    data class SetDirection(
        val address: LocoAddress,
        val direction: Direction,
    ) : SessionIntent

    data class EmergencyStop(val address: LocoAddress) : SessionIntent

    data class PressFunction(
        val address: LocoAddress,
        val function: Int,
    ) : SessionIntent

    data class ReleaseFunction(
        val address: LocoAddress,
        val function: Int,
    ) : SessionIntent

    data class ForceFunction(
        val address: LocoAddress,
        val function: Int,
        val on: Boolean,
    ) : SessionIntent

    data class SetFunctionMomentary(
        val address: LocoAddress,
        val function: Int,
        val momentary: Boolean,
    ) : SessionIntent

    data class SetSpeedStepMode(
        val address: LocoAddress,
        val mode: SpeedStepMode,
    ) : SessionIntent

    data class QuerySpeed(val address: LocoAddress) : SessionIntent

    data class QueryDirection(val address: LocoAddress) : SessionIntent

    data class QuerySpeedStepMode(val address: LocoAddress) : SessionIntent

    data class QueryMomentaryStates(val address: LocoAddress) : SessionIntent

    data class SetConsistLead(
        val address: LocoAddress,
        val lead: LocoAddress,
    ) : SessionIntent

    data class AddToConsist(
        val consist: LocoAddress,
        val name: String,
        val loco: LocoAddress,
        val forward: Boolean = true,
    ) : SessionIntent

    data class RemoveFromConsist(
        val consist: LocoAddress,
        val loco: LocoAddress,
    ) : SessionIntent

    data class SetConsistOrder(
        val consist: LocoAddress,
        val locos: List<LocoAddress>,
    ) : SessionIntent

    data class DeleteConsist(val consist: LocoAddress) : SessionIntent

    data class SetTrackPower(val state: TrackPowerState) : SessionIntent

    data class SetHeartbeatEnabled(val enabled: Boolean) : SessionIntent

    data object Quit : SessionIntent
}

/** One input to [SessionReducer]. Timestamps are caller-supplied monotonic milliseconds. */
sealed interface SessionInput {
    data class Event(
        val event: WiThrottleEvent,
        val atMillis: Long,
    ) : SessionInput

    data class Intent(
        val intent: SessionIntent,
        val atMillis: Long,
    ) : SessionInput

    data class Tick(val atMillis: Long) : SessionInput

    data class LinkUp(val atMillis: Long) : SessionInput

    data class LinkDown(val atMillis: Long) : SessionInput
}
