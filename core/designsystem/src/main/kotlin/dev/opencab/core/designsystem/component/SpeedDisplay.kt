package dev.opencab.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencab.core.designsystem.R
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class CabDirection {
    Forward,
    Reverse,
}

/**
 * Visual speed readout: a 270-degree instrument arc with a tabular numeral inside it.
 *
 * This component is presentation only. It does not accept drag input and has no command
 * side effects; the interactive throttle wraps it or replaces the arc surface.
 *
 * TalkBack receives one node with progress semantics and a state description such as
 * "84 percent, forward". The visible digits never animate; only the arc eases, so a stop is
 * visible without implying a physical deceleration that the app cannot measure.
 */
@Composable
fun SpeedDisplay(
    percent: Int?,
    modifier: Modifier = Modifier,
    direction: CabDirection? = null,
    stale: Boolean = false,
    enabled: Boolean = true,
    caption: String? = null,
    arcSize: Dp? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    val compact = CabTheme.sizing.speedArcCompact
    val maximum = CabTheme.sizing.speedArcMax
    val growth = ((fontScale - 1f) / 1f).coerceIn(0f, 1f)
    val diameter = arcSize ?: (compact + (maximum - compact) * growth)
    val directionLabel =
        when (direction) {
            CabDirection.Forward -> stringResource(R.string.cab_direction_forward)
            CabDirection.Reverse -> stringResource(R.string.cab_direction_reverse)
            null -> null
        }

    SpeedArcReadout(
        state =
        SpeedReadoutState(
            percent = percent,
            direction = direction,
            directionLabel = directionLabel,
            stale = stale,
            enabled = enabled,
            caption = caption,
        ),
        diameter = diameter,
        modifier = modifier,
    )
}

private data class SpeedReadoutState(
    val percent: Int?,
    val direction: CabDirection?,
    val directionLabel: String?,
    val stale: Boolean,
    val enabled: Boolean,
    val caption: String?,
)

@Composable
private fun SpeedArcReadout(
    state: SpeedReadoutState,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = CabTheme.colorScheme
    val targetProgress = (state.percent ?: 0).coerceIn(0, 100) / 100f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = CabTheme.motion.arcFill,
        label = "speedArc",
    )
    val description =
        when {
            state.percent == null -> stringResource(R.string.cab_speed_unknown)

            state.stale && state.directionLabel != null ->
                stringResource(R.string.cab_speed_readout_stale, state.percent, state.directionLabel)

            state.directionLabel != null ->
                stringResource(R.string.cab_speed_readout, state.percent, state.directionLabel)

            else -> stringResource(R.string.cab_speed_readout_no_direction, state.percent)
        }
    val resolvedCaption =
        state.caption ?: if (state.stale && state.percent != null) {
            stringResource(R.string.cab_speed_last_known, state.percent)
        } else {
            null
        }

    Box(
        modifier =
        modifier
            .size(diameter)
            .alpha(if (state.enabled) 1f else 0.7f)
            .clearAndSetSemantics {
                if (state.percent == null) {
                    contentDescription = description
                } else {
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            current = state.percent.coerceIn(0, 100) / 100f,
                            range = 0f..1f,
                        )
                    stateDescription = description
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SpeedArc(
            progress = animatedProgress,
            enabled = state.enabled,
        )
        SpeedNumerals(
            state = state,
            caption = resolvedCaption,
        )
    }
}

@Composable
private fun SpeedArc(
    progress: Float,
    enabled: Boolean,
) {
    val colors = CabTheme.colorScheme
    val speedStroke = CabTheme.sizing.speedStroke
    val trackColor = colors.surfaceVariant
    val progressColor = if (enabled) colors.primary else colors.outline
    val tickColor = colors.outlineVariant

    Canvas(Modifier.fillMaxSize()) {
        val strokePx = speedStroke.toPx()
        val radius = (min(size.width, size.height) - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSizePx = Size(radius * 2f, radius * 2f)

        drawArc(
            color = trackColor,
            startAngle = START_ANGLE,
            sweepAngle = SWEEP_ANGLE,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSizePx,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
        drawTicks(
            center = center,
            radius = radius - strokePx,
            color = tickColor,
        )
        drawArc(
            color = progressColor,
            startAngle = START_ANGLE,
            sweepAngle = SWEEP_ANGLE * progress,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSizePx,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun SpeedNumerals(
    state: SpeedReadoutState,
    caption: String?,
) {
    val colors = CabTheme.colorScheme
    val extended = CabTheme.extendedColors

    Column(
        modifier = Modifier.fillMaxWidth(0.68f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = state.percent?.toString() ?: "—",
            style = CabTheme.typography.speedValue,
            color = if (state.stale) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 40.sp, maxFontSize = 96.sp),
        )
        Text(
            text = stringResource(R.string.cab_speed_unit),
            style = CabTheme.typography.speedUnit,
            color = colors.onSurfaceVariant,
        )
        if (state.directionLabel != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector =
                    if (state.direction == CabDirection.Forward) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.primary,
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = state.directionLabel.uppercase(),
                    style = CabTheme.typography.status,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = caption,
                style = CabTheme.typography.speedCaption,
                color = if (state.stale) extended.caution else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun DrawScope.drawTicks(
    center: Offset,
    radius: Float,
    color: Color,
) {
    val tickLength = 8.dp.toPx()
    listOf(0, 25, 50, 75, 100).forEach { fraction ->
        val angle = START_ANGLE + SWEEP_ANGLE * fraction / 100f
        drawLine(
            color = color,
            start = arcOffset(center, angle, radius),
            end = arcOffset(center, angle, radius - tickLength),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun arcOffset(
    center: Offset,
    degrees: Float,
    radius: Float,
): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(
        x = center.x + cos(radians).toFloat() * radius,
        y = center.y + sin(radians).toFloat() * radius,
    )
}

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f

@CabPreview
@Composable
@Suppress("UnusedPrivateMember")
private fun SpeedDisplayPreview() {
    CabPreviewSurface {
        Box(contentAlignment = Alignment.Center) {
            SpeedDisplay(percent = 84, direction = CabDirection.Forward)
        }
    }
}
