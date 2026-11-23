package dev.opencab.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Haptic vocabulary for OpenCab.
 *
 * The token names describe the product moment, not the platform constant, so the mapping can
 * be revisited without touching call sites. Haptics are gated by the caller (the settings
 * toggle) and are never the only feedback channel.
 */
enum class CabHapticToken {
    /** A detent while dragging the throttle, roughly every 5%. */
    Detent,

    /** A limit was reached: 0% or the speed cap. */
    Limit,

    /** A deliberate action succeeded: acquire, direction change, stop. */
    Confirm,

    /** Something was refused: a blocked address or a rejected action. */
    Reject,

    ToggleOn,
    ToggleOff,
}

@Stable
class CabHaptics internal constructor(
    private val feedback: HapticFeedback,
    val enabled: Boolean,
) {
    fun perform(token: CabHapticToken) {
        if (!enabled) return
        feedback.performHapticFeedback(token.platformType)
    }
}

private val CabHapticToken.platformType: HapticFeedbackType
    get() =
        when (this) {
            CabHapticToken.Detent -> HapticFeedbackType.SegmentTick
            CabHapticToken.Limit -> HapticFeedbackType.LongPress
            CabHapticToken.Confirm -> HapticFeedbackType.Confirm
            CabHapticToken.Reject -> HapticFeedbackType.Reject
            CabHapticToken.ToggleOn -> HapticFeedbackType.ToggleOn
            CabHapticToken.ToggleOff -> HapticFeedbackType.ToggleOff
        }

@Composable
fun rememberCabHaptics(enabled: Boolean = true): CabHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback, enabled) { CabHaptics(feedback = feedback, enabled = enabled) }
}

private object NoOpHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

internal val LocalCabHaptics = staticCompositionLocalOf {
    CabHaptics(feedback = NoOpHapticFeedback, enabled = true)
}
