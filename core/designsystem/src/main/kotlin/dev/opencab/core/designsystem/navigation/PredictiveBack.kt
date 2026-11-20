package dev.opencab.core.designsystem.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * Draws the system's predictive-back progress as a subtle scale/fade of the content, then
 * invokes [onBack] when the gesture commits.
 *
 * This is intentionally visual-only: the caller still owns the navigation decision. The
 * handler is registered *after* the content so that it takes precedence over a nested
 * NavHost's own back callback; disable this container for destinations where the system
 * should exit the app instead.
 */
@Composable
fun CabPredictiveBackContainer(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = CabTheme.motion.reduced,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    Box(modifier = modifier) {
        Box(
            modifier =
            Modifier.graphicsLayer {
                if (!reducedMotion) {
                    val value = progress.value
                    scaleX = 1f - PREDICTIVE_SCALE * value
                    scaleY = 1f - PREDICTIVE_SCALE * value
                    alpha = 1f - PREDICTIVE_FADE * value
                }
            },
        ) {
            content()
        }
        PredictiveBackHandler(enabled = enabled) { events ->
            try {
                events.collect { event ->
                    progress.snapTo(event.progress)
                }
                onBack()
            } finally {
                if (progress.value != 0f) {
                    progress.snapTo(0f)
                }
            }
        }
    }
}

private const val PREDICTIVE_SCALE = 0.04f
private const val PREDICTIVE_FADE = 0.12f
