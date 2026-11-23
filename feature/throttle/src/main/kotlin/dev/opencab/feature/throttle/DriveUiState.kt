package dev.opencab.feature.throttle

import kotlin.math.roundToInt

/** The link state as the drive screen presents it. */
enum class DriveLink {
    Connected,
    Connecting,
    Reconnecting,
    Offline,
    Failed,
}

/** Whether the locomotive can currently accept commands. */
enum class DriveHealth {
    Online,
    Stale,
    Offline,
}

/** The acquisition state that affects what the screen can do. */
enum class DriveAcquisition {
    Idle,
    Requested,
    Acquired,
    InUse,
    TakingControl,
    Releasing,
    Lost,
    Failed,
}

/** A problem that needs a deliberate operator decision. */
enum class DriveIssue {
    InUse,
    AcquireTimedOut,
    CommandFailed,
}

enum class DriveDirection {
    Forward,
    Reverse,
}

enum class DriveSpeedUnit {
    KilometresPerHour,
    MilesPerHour,
}

/** The optional scale calibration used for an estimated scale speed. */
data class DriveScale(
    val maxScaleSpeed: Int,
    val unit: DriveSpeedUnit = DriveSpeedUnit.KilometresPerHour,
) {
    init {
        require(maxScaleSpeed > 0) { "Maximum scale speed must be positive" }
    }

    fun estimatedSpeed(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * maxScaleSpeed).roundToInt()
}

/** Haptics are gated by the system, the in-app toggle, and a future intensity setting. */
data class DriveHaptics(
    val enabled: Boolean = true,
    val intensity: Float = 1f,
) {
    fun allows(token: DriveHapticToken): Boolean {
        if (!enabled || intensity <= 0f) return false
        val threshold = when (token) {
            DriveHapticToken.Detent -> 0.15f
            DriveHapticToken.Limit -> 0.35f
            DriveHapticToken.Confirm -> 0.45f
        }
        return intensity >= threshold
    }
}

enum class DriveHapticToken {
    Detent,
    Limit,
    Confirm,
}

/** One rendered function key. */
data class DriveFunction(
    val number: Int,
    val label: String,
    val isOn: Boolean,
    val isMomentary: Boolean,
    val isEnabled: Boolean,
)

/** One acquired locomotive, decoupled from protocol types. */
data class DriveLoco(
    val address: String,
    val name: String,
    val speedStepCount: Int,
    val speedStep: Int,
    val speed: Float,
    val direction: DriveDirection,
    val functions: List<DriveFunction>,
    val health: DriveHealth,
    val acquisition: DriveAcquisition,
    val canControl: Boolean,
    val emergencyStop: Boolean = false,
    val issue: DriveIssue? = null,
) {
    init {
        require(speedStepCount > 0) { "Speed-step count must be positive" }
    }

    val isMoving: Boolean
        get() = speedStep > 0

    val normalizedSpeed: Float
        get() = (speedStep.toFloat() / speedStepCount).coerceIn(0f, 1f)
}

/** Everything the drive screen renders. */
data class DriveUiState(
    val layoutName: String? = null,
    val link: DriveLink = DriveLink.Offline,
    val reconnectAttempt: Int? = null,
    val loco: DriveLoco? = null,
    val scale: DriveScale? = null,
    val haptics: DriveHaptics = DriveHaptics(),
    val trackPowerOn: Boolean = true,
) {
    val controlsEnabled: Boolean
        get() = loco != null && loco.canControl && link == DriveLink.Connected
}

/**
 * The actions the drive screen can request.
 *
 * An interface with empty defaults keeps the composable signature small and lets tests override
 * only the action under test.
 */
@Suppress("TooManyFunctions")
interface DriveActions {
    fun onSpeedChanged(speed: Float) = Unit

    fun onNudge(steps: Int) = Unit

    fun onStop() = Unit

    fun onDirectionChange(direction: DriveDirection) = Unit

    fun onToggleFunction(number: Int) = Unit

    fun onFunctionHoldStart(number: Int) = Unit

    fun onFunctionHoldEnd(number: Int) = Unit

    fun onTakeControl() = Unit

    fun onRetryAcquire() = Unit

    fun onRelease() = Unit

    fun onChooseLocomotive() = Unit

    fun onOpenConnection() = Unit

    companion object {
        val None: DriveActions = object : DriveActions {}
    }
}
