package dev.opencab.core.data.throttle

import dev.opencab.core.data.connection.DccClient
import dev.opencab.core.model.ConnectionError
import dev.opencab.core.protocol.AddressType
import dev.opencab.core.protocol.Direction
import dev.opencab.core.protocol.LocoAddress
import dev.opencab.core.protocol.SpeedStepMode
import dev.opencab.core.protocol.TrackPowerState
import kotlin.math.roundToInt

/** The three speed-step modes the drive screen exposes. */
enum class SpeedSteps(
    val steps: Int,
    val protocolMode: SpeedStepMode,
) {
    FOURTEEN(14, SpeedStepMode.NmraDcc14),
    TWENTY_EIGHT(28, SpeedStepMode.NmraDcc28),
    ONE_HUNDRED_TWENTY_EIGHT(128, SpeedStepMode.NmraDcc128),
    ;

    companion object {
        fun fromSteps(steps: Int): SpeedSteps = entries.firstOrNull { it.steps == steps } ?: ONE_HUNDRED_TWENTY_EIGHT

        fun fromProtocol(mode: SpeedStepMode): SpeedSteps = when (mode) {
            SpeedStepMode.NmraDcc14 -> FOURTEEN
            SpeedStepMode.NmraDcc28 -> TWENTY_EIGHT
            else -> ONE_HUNDRED_TWENTY_EIGHT
        }
    }
}

/** The link the engine is attached to. Unlinked is an intentional detach, not a failure. */
enum class ThrottleLinkState {
    UNLINKED,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED,
}

/** Local acquisition state for one slot. [IN_USE] means the server wants a steal confirmation. */
enum class ThrottleAcquisitionState {
    IDLE,
    REQUESTED,
    ACQUIRED,
    IN_USE,
    TAKING_CONTROL,
    RELEASING,
    LOST,
    FAILED,
}

/** Whether this slot can currently be driven. Stale slots keep their last known values. */
enum class ThrottleHealth {
    ONLINE,
    STALE,
    OFFLINE,
}

/** A user-visible problem attached to one slot. */
sealed interface ThrottleIssue {
    /** The server offered a steal because another throttle holds this address. */
    data class InUse(val address: LocoAddress) : ThrottleIssue

    /** Acquire was sent but the server never answered within the configured window. */
    data class AcquireTimedOut(val address: LocoAddress) : ThrottleIssue

    /** The transport refused or failed a command for this slot. */
    data class CommandFailed(val address: LocoAddress) : ThrottleIssue
}

/** One function key, numbered 0–28 by default. Disabled keys ignore input. */
data class ThrottleFunctionUiState(
    val number: Int,
    val label: String,
    val isOn: Boolean,
    val isMomentary: Boolean,
    val isEnabled: Boolean,
)

/** Immutable view of one throttle slot. */
data class ThrottleSlotUiState(
    val address: LocoAddress,
    val name: String?,
    val speedSteps: SpeedSteps,
    /** Normalized 0.0..1.0 so the UI never depends on a particular speed-step mode. */
    val speed: Double,
    val direction: Direction,
    val functions: List<ThrottleFunctionUiState>,
    val acquisition: ThrottleAcquisitionState,
    val health: ThrottleHealth,
    val desired: Boolean,
    val emergencyStop: Boolean,
    val issue: ThrottleIssue? = null,
) {
    val addressType: AddressType
        get() = address.type

    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Loco ${address.number}"

    /** The current position in this slot's speed-step mode. */
    val step: Int
        get() = (speed * speedSteps.steps).roundToInt().coerceIn(0, speedSteps.steps)

    /** Input is enabled only while the link is live and the server has granted the loco. */
    val canControl: Boolean
        get() = health == ThrottleHealth.ONLINE && acquisition == ThrottleAcquisitionState.ACQUIRED

    fun function(number: Int): ThrottleFunctionUiState? = functions.firstOrNull { it.number == number }
}

/** Immutable snapshot for the UI. Every field is safe to render without touching the wire. */
data class ThrottleUiState(
    val link: ThrottleLinkState = ThrottleLinkState.UNLINKED,
    val reconnectAttempt: Int? = null,
    val linkError: ConnectionError? = null,
    val trackPower: TrackPowerState = TrackPowerState.UNKNOWN,
    val slots: List<ThrottleSlotUiState> = emptyList(),
    val lastAlert: String? = null,
) {
    val canControl: Boolean
        get() = link == ThrottleLinkState.CONNECTED

    fun slot(address: LocoAddress): ThrottleSlotUiState? = slots.firstOrNull { it.address == address }
}

/** Engine tuning. Defaults match the product's 10 Hz command budget and 5 s acquire window. */
data class ThrottleConfig(
    val speedWriteIntervalMillis: Long = 100,
    val momentaryPulseMillis: Long = 250,
    val acquireTimeoutMillis: Long = 5_000,
    val firstFunction: Int = 0,
    val lastFunction: Int = 28,
) {
    init {
        require(speedWriteIntervalMillis > 0) { "Speed write interval must be positive" }
        require(momentaryPulseMillis > 0) { "Momentary pulse must be positive" }
        require(acquireTimeoutMillis > 0) { "Acquire timeout must be positive" }
        require(firstFunction >= 0) { "First function must not be negative" }
        require(lastFunction <= 28) { "Last function must be 28 or lower" }
        require(firstFunction <= lastFunction) { "Function range is empty" }
    }

    val functionRange: IntRange = firstFunction..lastFunction
}

/** Everything the UI can ask the engine to do. All intents are safe to dispatch repeatedly. */
sealed interface ThrottleIntent {
    /** Attach the engine to a transport. A previous link is detached first. */
    data class Link(val client: DccClient) : ThrottleIntent

    /** Detach from the transport without releasing desired slots. */
    data object Unlink : ThrottleIntent

    data class Acquire(
        val address: LocoAddress,
        val name: String? = null,
        val speedSteps: SpeedSteps = SpeedSteps.ONE_HUNDRED_TWENTY_EIGHT,
        val rosterEntryId: String? = null,
    ) : ThrottleIntent

    data class Release(val address: LocoAddress) : ThrottleIntent

    /** [speed] is normalized 0.0..1.0. Values outside the range are clamped. */
    data class SetSpeed(
        val address: LocoAddress,
        val speed: Double,
    ) : ThrottleIntent {
        init {
            require(speed.isFinite()) { "Speed must be finite" }
        }
    }

    /** Move by whole speed steps in the slot's configured mode. */
    data class Nudge(
        val address: LocoAddress,
        val steps: Int,
    ) : ThrottleIntent

    data class SetDirection(
        val address: LocoAddress,
        val direction: Direction,
    ) : ThrottleIntent

    data class SetSpeedSteps(
        val address: LocoAddress,
        val speedSteps: SpeedSteps,
    ) : ThrottleIntent

    /** Toggle a latching function, or pulse a momentary one. */
    data class ToggleFunction(
        val address: LocoAddress,
        val function: Int,
    ) : ThrottleIntent

    /** Set an absolute function state. Momentary presses use this while held. */
    data class SetFunction(
        val address: LocoAddress,
        val function: Int,
        val on: Boolean,
    ) : ThrottleIntent

    /** Per-loco emergency stop. This is independent of global track power. */
    data class EmergencyStop(val address: LocoAddress) : ThrottleIntent

    /** Normal stop for one loco; bypasses speed coalescing. */
    data class Stop(val address: LocoAddress) : ThrottleIntent

    /** Accept a server steal prompt for an address held by another throttle. */
    data class TakeControl(val address: LocoAddress) : ThrottleIntent
}
