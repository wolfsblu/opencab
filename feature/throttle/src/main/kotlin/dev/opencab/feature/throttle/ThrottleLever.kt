package dev.opencab.feature.throttle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencab.core.designsystem.theme.CabElevationLevel
import dev.opencab.core.designsystem.theme.CabHapticToken
import dev.opencab.core.designsystem.theme.CabTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/** Local lever position. Kept outside the engine state so a drag never waits on the network. */
@Stable
internal class LeverState(
    initialStep: Int,
    initialStepCount: Int,
) {
    var fraction by mutableFloatStateOf((initialStep.toFloat() / initialStepCount).coerceIn(0f, 1f))
        private set
    var stepCount by mutableIntStateOf(initialStepCount)
        private set
    var dragging by mutableStateOf(false)

    val step: Int
        get() = (fraction * stepCount).roundToInt().coerceIn(0, stepCount)

    fun sync(step: Int, stepCount: Int) {
        this.stepCount = stepCount
        if (!dragging) {
            fraction = (step.toFloat() / stepCount).coerceIn(0f, 1f)
        }
    }

    fun moveTo(target: Float) {
        fraction = target.coerceIn(0f, 1f)
    }
}

/**
 * The lever assembly: a recessed step readout, the physical lever rail, and the Direction/Stop
 * control deck all share one instrument surface. The deck is deliberately part of the throttle
 * rather than a toolbar floating beside it.
 */
@Composable
internal fun DriveLeverPanel(
    loco: DriveLoco,
    enabled: Boolean,
    scale: DriveScale?,
    haptics: DriveHaptics,
    actions: DriveActions,
    onReverseRequest: (DriveDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lever = remember { LeverState(loco.speedStep, loco.speedStepCount) }
    LaunchedEffect(loco.speedStep, loco.speedStepCount) {
        lever.sync(loco.speedStep, loco.speedStepCount)
    }
    val performHaptic = rememberDriveHaptics(haptics)
    AnnounceStepChanges(lever)

    Surface(
        modifier = modifier.fillMaxWidth().testTag(DriveTestTags.LEVER_PANEL),
        shape = CabTheme.shapes.panel,
        color = CabTheme.elevation.surfaceFor(
            level = CabElevationLevel.Raised,
            colorScheme = CabTheme.colorScheme,
        ),
        contentColor = CabTheme.colorScheme.onSurface,
    ) {
        BoxWithConstraints(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = CabTheme.spacing.sm, vertical = CabTheme.spacing.sm),
        ) {
            val assembly =
                LeverAssembly(
                    loco = loco,
                    lever = lever,
                    enabled = enabled,
                    scale = scale,
                    haptics = haptics,
                    actions = actions,
                    onReverseRequest = onReverseRequest,
                )
            if (maxWidth > 700.dp || maxHeight > 700.dp) {
                ConsoleLeverAssembly(state = assembly)
            } else if (maxWidth > maxHeight * 1.15f) {
                WideLeverAssembly(state = assembly)
            } else {
                TallLeverAssembly(state = assembly)
            }
        }
    }
}

private data class LeverAssembly(
    val loco: DriveLoco,
    val lever: LeverState,
    val enabled: Boolean,
    val scale: DriveScale?,
    val haptics: DriveHaptics,
    val actions: DriveActions,
    val onReverseRequest: (DriveDirection) -> Unit,
)

/**
 * The readout + lever stage used by the portrait tabbed instrument. The control deck is composed
 * separately and pinned below the tabs, so Stop and Direction remain available on every tab.
 */
@Composable
internal fun DriveThrottleStage(
    loco: DriveLoco,
    enabled: Boolean,
    scale: DriveScale?,
    haptics: DriveHaptics,
    actions: DriveActions,
    modifier: Modifier = Modifier,
) {
    val lever = remember { LeverState(loco.speedStep, loco.speedStepCount) }
    LaunchedEffect(loco.speedStep, loco.speedStepCount) {
        lever.sync(loco.speedStep, loco.speedStepCount)
    }
    val performHaptic = rememberDriveHaptics(haptics)
    AnnounceStepChanges(lever)
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReadoutWindow(
            loco = loco,
            lever = lever,
            scale = scale,
            enabled = enabled,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        ThrottleLever(
            loco = loco,
            lever = lever,
            enabled = enabled,
            actions = actions,
            performHaptic = performHaptic,
            modifier = Modifier.weight(0.82f).fillMaxHeight(),
        )
    }
}

/**
 * Large-screen assembly: a capped readout and control deck form a centred console beside a lever
 * that runs the full height of the instrument bay. This prevents the readout from stretching into
 * a mostly empty window on tablets.
 */
@Composable
private fun ConsoleLeverAssembly(
    state: LeverAssembly,
    modifier: Modifier = Modifier,
) {
    val performHaptic = rememberDriveHaptics(state.haptics)
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            ReadoutWindow(
                loco = state.loco,
                lever = state.lever,
                scale = state.scale,
                enabled = state.enabled,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .fillMaxHeight(),
            )
            Spacer(Modifier.height(CabTheme.spacing.sm))
            DriveControlDeck(
                loco = state.loco,
                enabled = state.enabled,
                haptics = state.haptics,
                actions = state.actions,
                onReverseRequest = state.onReverseRequest,
                modifier = Modifier.fillMaxWidth().testTag(DriveTestTags.CONTROL_DECK),
            )
        }
        ThrottleLever(
            loco = state.loco,
            lever = state.lever,
            enabled = state.enabled,
            actions = state.actions,
            performHaptic = performHaptic,
            modifier = Modifier.weight(0.82f).fillMaxHeight(),
        )
    }
}

/**
 * Landscape/tablet assembly: the readout and the control deck form the left stack while the lever
 * runs the full height of the instrument bay.
 */
@Composable
private fun WideLeverAssembly(
    state: LeverAssembly,
    modifier: Modifier = Modifier,
) {
    val performHaptic = rememberDriveHaptics(state.haptics)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val spacing = CabTheme.spacing.sm
        val leftWidth = (maxWidth * 0.56f).coerceAtMost(520.dp)
        val leverWidth = (maxWidth - leftWidth - spacing).coerceAtMost(420.dp)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(leftWidth).fillMaxHeight()) {
                ReadoutWindow(
                    loco = state.loco,
                    lever = state.lever,
                    scale = state.scale,
                    enabled = state.enabled,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Spacer(Modifier.height(CabTheme.spacing.sm))
                DriveControlDeck(
                    loco = state.loco,
                    enabled = state.enabled,
                    haptics = state.haptics,
                    actions = state.actions,
                    onReverseRequest = state.onReverseRequest,
                    modifier = Modifier.fillMaxWidth().testTag(DriveTestTags.CONTROL_DECK),
                )
            }
            Spacer(Modifier.width(spacing))
            ThrottleLever(
                loco = state.loco,
                lever = state.lever,
                enabled = state.enabled,
                actions = state.actions,
                performHaptic = performHaptic,
                modifier = Modifier.width(leverWidth).fillMaxHeight(),
            )
        }
    }
}

/** Portrait assembly: the readout and lever share the top row; the control deck spans below. */
@Composable
private fun TallLeverAssembly(
    state: LeverAssembly,
    modifier: Modifier = Modifier,
) {
    val performHaptic = rememberDriveHaptics(state.haptics)
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadoutWindow(
                loco = state.loco,
                lever = state.lever,
                scale = state.scale,
                enabled = state.enabled,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ThrottleLever(
                loco = state.loco,
                lever = state.lever,
                enabled = state.enabled,
                actions = state.actions,
                performHaptic = performHaptic,
                modifier = Modifier.weight(0.82f).fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(CabTheme.spacing.sm))
        HorizontalDivider(color = CabTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(CabTheme.spacing.sm))
        DriveControlDeck(
            loco = state.loco,
            enabled = state.enabled,
            haptics = state.haptics,
            actions = state.actions,
            onReverseRequest = state.onReverseRequest,
            modifier = Modifier.fillMaxWidth().testTag(DriveTestTags.CONTROL_DECK),
        )
    }
}

@Composable
private fun ReadoutWindow(
    loco: DriveLoco,
    lever: LeverState,
    scale: DriveScale?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = CabTheme.shapes.medium
    Surface(
        modifier =
        modifier
            .testTag(DriveTestTags.READOUT)
            .border(
                width = 1.dp,
                color = CabTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = shape,
            ),
        shape = shape,
        color = CabTheme.colorScheme.surfaceContainerLowest,
        contentColor = CabTheme.colorScheme.onSurface,
    ) {
        SpeedReadout(
            loco = loco,
            lever = lever,
            scale = scale,
            enabled = enabled,
            modifier = Modifier.fillMaxSize().padding(CabTheme.spacing.md),
        )
    }
}

@Composable
internal fun DriveControlDeck(
    loco: DriveLoco,
    enabled: Boolean,
    haptics: DriveHaptics,
    actions: DriveActions,
    onReverseRequest: (DriveDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val performHaptic = rememberDriveHaptics(haptics)
    val target =
        if (loco.direction == DriveDirection.Forward) {
            DriveDirection.Reverse
        } else {
            DriveDirection.Forward
        }
    val stopDescription =
        stringResource(R.string.drive_stop_description, loco.speedStep, loco.speedStepCount)
    Row(
        modifier = modifier.height(CabTheme.sizing.controlDeck),
        horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
    ) {
        DirectionKey(
            direction = loco.direction,
            enabled = enabled,
            onClick = {
                if (loco.isMoving) {
                    onReverseRequest(target)
                } else {
                    performHaptic(DriveHapticToken.Confirm)
                    actions.onDirectionChange(target)
                }
            },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StopKey(
            loco = loco,
            description = stopDescription,
            onClick = {
                performHaptic(DriveHapticToken.Confirm)
                actions.onStop()
            },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun DirectionKey(
    direction: DriveDirection,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        if (direction == DriveDirection.Forward) {
            R.string.drive_direction_short_forward
        } else {
            R.string.drive_direction_short_reverse
        },
    )
    val directionState = stringResource(R.string.drive_direction_state, directionLabel(direction))
    val directionDescription = stringResource(R.string.drive_direction)
    val shape = CabTheme.shapes.control
    val contentColor =
        if (enabled) {
            CabTheme.colorScheme.onSurface
        } else {
            CabTheme.extendedColors.disabledContent
        }
    val borderColor =
        if (enabled) {
            CabTheme.colorScheme.outlineVariant
        } else {
            CabTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
        modifier
            .testTag(DriveTestTags.DIRECTION)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .semantics {
                contentDescription = directionDescription
                stateDescription = directionState
            },
        shape = shape,
        color =
        if (enabled) {
            CabTheme.colorScheme.surfaceContainerHigh
        } else {
            CabTheme.colorScheme.surfaceContainerLow
        },
        contentColor = contentColor,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = CabTheme.spacing.sm, vertical = CabTheme.spacing.xs),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                if (direction == DriveDirection.Forward) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
                modifier = Modifier.size(CabTheme.sizing.iconMedium),
                tint = if (enabled) CabTheme.colorScheme.primary else contentColor,
            )
            Spacer(Modifier.width(CabTheme.spacing.xxs))
            Text(
                text = label.uppercase(),
                style = CabTheme.typography.sectionTitle,
                color = contentColor,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 20.sp),
            )
        }
    }
}

@Composable
private fun StopKey(
    loco: DriveLoco,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = loco.issue != DriveIssue.InUse
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
        modifier
            .testTag(DriveTestTags.STOP)
            .semantics { stateDescription = description },
        shape = CabTheme.shapes.control,
        colors =
        ButtonDefaults.buttonColors(
            containerColor = CabTheme.extendedColors.stop,
            contentColor = CabTheme.extendedColors.onStop,
            disabledContainerColor = CabTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = CabTheme.extendedColors.disabledContent,
        ),
        contentPadding =
        PaddingValues(
            horizontal = CabTheme.spacing.md,
            vertical = CabTheme.spacing.sm,
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                Modifier
                    .size(12.dp)
                    .background(LocalContentColor.current, CabTheme.shapes.indicator),
            )
            Spacer(Modifier.width(CabTheme.spacing.sm))
            Text(
                text = stringResource(R.string.drive_stop_short),
                style = CabTheme.typography.sectionTitle,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 24.sp),
            )
        }
    }
}

@Composable
private fun SpeedReadout(
    loco: DriveLoco,
    lever: LeverState,
    scale: DriveScale?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val directionLabel = directionLabel(loco.direction)
    val stale = loco.health == DriveHealth.Stale
    BoxWithConstraints(modifier = modifier) {
        val showStepLabel = maxHeight >= 260.dp
        val estimateScale = scale?.takeIf { maxHeight >= 300.dp }
        val showStale = stale && maxHeight >= 240.dp
        val density = LocalDensity.current
        val numberMax =
            with(density) {
                (maxHeight * 0.38f).coerceIn(40.dp, 112.dp).toSp()
            }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = if (estimateScale != null) Arrangement.SpaceBetween else Arrangement.Center,
        ) {
            Column {
                if (showStepLabel) {
                    Text(
                        text = stringResource(R.string.drive_step_label),
                        style = CabTheme.typography.speedCaption,
                        color = CabTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.drive_step_value, lever.step),
                    style = CabTheme.typography.speedValue,
                    color =
                    if (enabled) {
                        CabTheme.colorScheme.onSurface
                    } else {
                        CabTheme.extendedColors.disabledContent
                    },
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = numberMax),
                    modifier = Modifier.testTag(DriveTestTags.STEP),
                )
                Text(
                    text = stringResource(R.string.drive_step_total, lever.stepCount),
                    style = CabTheme.typography.speedUnit,
                    color = CabTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(CabTheme.spacing.xs))
                DirectionBadge(direction = loco.direction, label = directionLabel, stale = stale)
                if (loco.speedStep == loco.speedStepCount) {
                    Spacer(Modifier.height(CabTheme.spacing.xxs))
                    MaxBadge()
                }
                if (showStale) {
                    Spacer(Modifier.height(CabTheme.spacing.xs))
                    Text(
                        text = stringResource(R.string.drive_last_known),
                        style = CabTheme.typography.speedCaption,
                        color = CabTheme.extendedColors.caution,
                    )
                }
            }
            if (estimateScale != null) {
                EstimatedSpeed(estimateScale, lever.step.toFloat() / lever.stepCount)
            }
        }
    }
}

@Composable
private fun DirectionBadge(
    direction: DriveDirection,
    label: String,
    stale: Boolean,
) {
    Surface(
        shape = CabTheme.shapes.small,
        color =
        if (stale) {
            CabTheme.colorScheme.surfaceContainerHigh
        } else {
            CabTheme.colorScheme.primaryContainer
        },
        contentColor =
        if (stale) {
            CabTheme.colorScheme.onSurfaceVariant
        } else {
            CabTheme.colorScheme.onPrimaryContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CabTheme.spacing.xs, vertical = CabTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                if (direction == DriveDirection.Forward) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
                modifier = Modifier.size(CabTheme.sizing.iconSmall),
                tint = if (stale) CabTheme.colorScheme.onSurfaceVariant else CabTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(CabTheme.spacing.xxs))
            Text(
                text = label.uppercase(),
                style = CabTheme.typography.status,
                color =
                if (stale) {
                    CabTheme.colorScheme.onSurfaceVariant
                } else {
                    CabTheme.colorScheme.onPrimaryContainer
                },
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 13.sp),
            )
        }
    }
}

@Composable
private fun MaxBadge() {
    Surface(
        shape = CabTheme.shapes.small,
        color = CabTheme.colorScheme.tertiaryContainer,
        contentColor = CabTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = stringResource(R.string.drive_max),
            style = CabTheme.typography.status,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = CabTheme.spacing.xs, vertical = 2.dp),
        )
    }
}

@Composable
private fun EstimatedSpeed(scale: DriveScale, fraction: Float) {
    val estimated = scale.estimatedSpeed(fraction)
    val text = when (scale.unit) {
        DriveSpeedUnit.KilometresPerHour ->
            stringResource(R.string.drive_estimate_kmh, estimated)

        DriveSpeedUnit.MilesPerHour ->
            stringResource(R.string.drive_estimate_mph, estimated)
    }
    val unitDescription = when (scale.unit) {
        DriveSpeedUnit.KilometresPerHour ->
            stringResource(R.string.drive_estimate_kmh_description)

        DriveSpeedUnit.MilesPerHour ->
            stringResource(R.string.drive_estimate_mph_description)
    }
    val description =
        stringResource(R.string.drive_estimate_description, estimated, unitDescription)
    Column {
        HorizontalDivider(color = CabTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        Spacer(Modifier.height(CabTheme.spacing.sm))
        Text(
            text = text,
            style = CabTheme.typography.speedCaption,
            color = CabTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 13.sp),
            modifier =
            Modifier
                .testTag(DriveTestTags.ESTIMATE)
                .semanticsDescription(description),
        )
        Text(
            text = stringResource(R.string.drive_scale_caption),
            style = CabTheme.typography.speedCaption,
            color = CabTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            maxLines = 2,
        )
    }
}

private fun Modifier.semanticsDescription(
    description: String,
): Modifier = semantics { contentDescription = description }

private data class LeverLabels(
    val description: String,
    val state: String,
    val stop: String,
    val forward: String,
    val reverse: String,
)

@Composable
private fun ThrottleLever(
    loco: DriveLoco,
    lever: LeverState,
    enabled: Boolean,
    actions: DriveActions,
    performHaptic: (DriveHapticToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(0) }
    val handleHeight = CabTheme.sizing.leverHandleHeight
    val handleHeightPx = with(density) { handleHeight.toPx() }
    val travel = (heightPx - handleHeightPx).coerceAtLeast(1f)
    val handleScale by animateFloatAsState(
        targetValue = if (lever.dragging) 1.035f else 1f,
        animationSpec = CabTheme.motion.pressFeedback,
        label = "leverHandleScale",
    )
    val labels = LeverLabels(
        description = stringResource(R.string.drive_lever_description),
        state = stringResource(
            R.string.drive_step_state,
            lever.step,
            lever.stepCount,
            directionLabel(loco.direction),
        ),
        stop = stringResource(R.string.drive_stop),
        forward = stringResource(R.string.drive_forward),
        reverse = stringResource(R.string.drive_reverse),
    )

    BoxWithConstraints(
        modifier =
        modifier
            .onSizeChanged { heightPx = it.height }
            .testTag(DriveTestTags.LEVER)
            .leverKeyboard(actions, performHaptic)
            .focusable()
            .leverSemantics(lever, enabled, labels, actions, performHaptic),
        contentAlignment = Alignment.TopCenter,
    ) {
        val handleWidth =
            (maxWidth - 8.dp)
                .coerceAtMost(CabTheme.sizing.leverHandleWidth)
                .coerceAtLeast(48.dp)
        Box(modifier = Modifier.fillMaxSize()) {
            LeverTrack(
                fraction = lever.fraction,
                handleHeightPx = handleHeightPx,
                enabled = enabled,
            )
            LeverHandle(
                enabled = enabled,
                width = handleWidth,
                modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(
                            x = 0,
                            y = ((1f - lever.fraction) * travel).roundToInt(),
                        )
                    }
                    .testTag(DriveTestTags.HANDLE)
                    .graphicsLayer {
                        scaleX = handleScale
                        scaleY = handleScale
                    }
                    .leverDrag(lever, enabled, travel, actions, performHaptic),
            )
        }
    }
}

private fun Modifier.leverKeyboard(
    actions: DriveActions,
    performHaptic: (DriveHapticToken) -> Unit,
): Modifier = onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
    val size = if (event.isShiftPressed) 10 else 1
    when (event.key) {
        Key.DirectionUp -> {
            actions.onNudge(size)
            performHaptic(DriveHapticToken.Detent)
            true
        }

        Key.DirectionDown -> {
            actions.onNudge(-size)
            performHaptic(DriveHapticToken.Detent)
            true
        }

        else -> false
    }
}

private fun Modifier.leverSemantics(
    lever: LeverState,
    enabled: Boolean,
    labels: LeverLabels,
    actions: DriveActions,
    performHaptic: (DriveHapticToken) -> Unit,
): Modifier = semantics {
    contentDescription = labels.description
    stateDescription = labels.state
    progressBarRangeInfo =
        ProgressBarRangeInfo(
            current = lever.fraction,
            range = 0f..1f,
            steps = (lever.stepCount - 1).coerceAtLeast(0),
        )
    if (enabled) {
        setProgress { target ->
            val step = (target.coerceIn(0f, 1f) * lever.stepCount).roundToInt()
            lever.moveTo(target)
            performHaptic(
                if (step == 0 || step == lever.stepCount) {
                    DriveHapticToken.Limit
                } else {
                    DriveHapticToken.Detent
                },
            )
            actions.onSpeedChanged(target)
            true
        }
        customActions = listOf(
            CustomAccessibilityAction(labels.stop) {
                actions.onStop()
                true
            },
            CustomAccessibilityAction(labels.forward) {
                actions.onDirectionChange(DriveDirection.Forward)
                true
            },
            CustomAccessibilityAction(labels.reverse) {
                actions.onDirectionChange(DriveDirection.Reverse)
                true
            },
        )
    }
}

private fun Modifier.leverDrag(
    lever: LeverState,
    enabled: Boolean,
    travel: Float,
    actions: DriveActions,
    performHaptic: (DriveHapticToken) -> Unit,
): Modifier = pointerInput(enabled, travel) {
    if (!enabled) return@pointerInput
    var rawY = 0f
    var lastStep = lever.step
    detectDragGestures(
        onDragStart = { offset ->
            lever.dragging = true
            rawY = (1f - lever.fraction) * travel + offset.y
        },
        onDrag = { change, dragAmount ->
            change.consume()
            rawY = (rawY + dragAmount.y).coerceIn(0f, travel)
            lever.moveTo(1f - rawY / travel)
            if (lever.step != lastStep) {
                lastStep = lever.step
                performHaptic(
                    if (lastStep == 0 || lastStep == lever.stepCount) {
                        DriveHapticToken.Limit
                    } else {
                        DriveHapticToken.Detent
                    },
                )
            }
            actions.onSpeedChanged(lever.fraction)
        },
        onDragEnd = {
            lever.dragging = false
        },
        onDragCancel = {
            lever.dragging = false
        },
    )
}

@Composable
private fun LeverTrack(
    fraction: Float,
    handleHeightPx: Float,
    enabled: Boolean,
) {
    val colors = CabTheme.colorScheme
    val trackColor = colors.surfaceContainerHighest
    val activeColor = if (enabled) colors.primary else colors.outline
    val tickColor = if (enabled) colors.outline else colors.outlineVariant
    val railWidth = CabTheme.sizing.leverTrackWidth
    Canvas(Modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val handleTop = handleHeightPx / 2f
        val handleBottom = size.height - handleHeightPx / 2f
        val railPx = railWidth.toPx()
        val left = centerX - railPx / 2f
        val corner = CornerRadius(railPx / 2f, railPx / 2f)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(left, 0f),
            size = Size(railPx, size.height),
            cornerRadius = corner,
        )

        val handleCenter = handleBottom - (handleBottom - handleTop) * fraction
        if (handleCenter < size.height - 1f) {
            drawLine(
                color = activeColor,
                start = Offset(centerX, handleCenter),
                end = Offset(centerX, size.height - railPx / 2f),
                strokeWidth = railPx - 6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        drawLine(
            color = colors.outlineVariant.copy(alpha = 0.65f),
            start = Offset(centerX, railPx / 2f),
            end = Offset(centerX, size.height - railPx / 2f),
            strokeWidth = 1.dp.toPx(),
        )

        for (index in 0..8) {
            val y = handleBottom - (handleBottom - handleTop) * index / 8f
            val major = index % 2 == 0
            val length = if (major) 14.dp.toPx() else 8.dp.toPx()
            drawLine(
                color = tickColor,
                start = Offset(left - 5.dp.toPx(), y),
                end = Offset(left - 5.dp.toPx() - length, y),
                strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        drawLine(
            color = tickColor,
            start = Offset(left - 2.dp.toPx(), 0f),
            end = Offset(left + railPx + 2.dp.toPx(), 0f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tickColor,
            start = Offset(left - 2.dp.toPx(), size.height),
            end = Offset(left + railPx + 2.dp.toPx(), size.height),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun LeverHandle(
    enabled: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = CabTheme.shapes.control
    Surface(
        modifier = modifier.width(width).height(CabTheme.sizing.leverHandleHeight),
        shape = shape,
        color =
        if (enabled) {
            CabTheme.colorScheme.primary
        } else {
            CabTheme.colorScheme.surfaceContainerHighest
        },
        contentColor =
        if (enabled) {
            CabTheme.colorScheme.onPrimary
        } else {
            CabTheme.colorScheme.onSurfaceVariant
        },
        border =
        BorderStroke(
            width = 1.dp,
            color =
            if (enabled) {
                CabTheme.colorScheme.primary
            } else {
                CabTheme.colorScheme.outlineVariant
            },
        ),
        shadowElevation = CabTheme.elevation.shadowFor(CabElevationLevel.Raised),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Surface(
                        modifier = Modifier.width(42.dp).height(2.dp),
                        shape = CircleShape,
                        color = LocalContentColor.current.copy(alpha = if (enabled) 0.75f else 0.4f),
                    ) {}
                }
            }
        }
    }
}

@Composable
internal fun rememberDriveHaptics(haptics: DriveHaptics): (DriveHapticToken) -> Unit {
    val haptic = CabTheme.haptics
    return remember(haptics, haptic) {
        { token: DriveHapticToken ->
            if (haptics.allows(token)) haptic.perform(token.toCabToken())
        }
    }
}

@Composable
private fun AnnounceStepChanges(lever: LeverState) {
    val resources = LocalResources.current
    val view = LocalView.current
    LaunchedEffect(lever) {
        snapshotFlow { lever.step to lever.stepCount }
            .map { (step, count) -> (step * 10 / count).coerceIn(0, 10) }
            .distinctUntilChanged()
            .drop(1)
            .collectLatest {
                kotlinx.coroutines.delay(600)
                view.announceForAccessibility(
                    resources.getString(
                        R.string.drive_announcement,
                        lever.step,
                        lever.stepCount,
                    ),
                )
            }
    }
}

@Composable
private fun directionLabel(direction: DriveDirection): String = stringResource(
    if (direction == DriveDirection.Forward) {
        R.string.drive_forward
    } else {
        R.string.drive_reverse
    },
)

private fun DriveHapticToken.toCabToken(): CabHapticToken = when (this) {
    DriveHapticToken.Detent -> CabHapticToken.Detent
    DriveHapticToken.Limit -> CabHapticToken.Limit
    DriveHapticToken.Confirm -> CabHapticToken.Confirm
}
