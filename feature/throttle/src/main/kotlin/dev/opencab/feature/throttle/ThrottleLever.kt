package dev.opencab.feature.throttle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.geometry.Offset
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

@Composable
internal fun DriveLeverPanel(
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CabTheme.shapes.panel,
        color = CabTheme.elevation.surfaceFor(
            level = CabElevationLevel.Raised,
            colorScheme = CabTheme.colorScheme,
        ),
        contentColor = CabTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(CabTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeedReadout(
                loco = loco,
                lever = lever,
                scale = scale,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            ThrottleLever(
                loco = loco,
                lever = lever,
                enabled = enabled,
                actions = actions,
                performHaptic = performHaptic,
                modifier = Modifier.weight(0.8f).widthIn(min = 96.dp, max = 168.dp).fillMaxHeight(),
            )
            NudgeButtons(
                enabled = enabled,
                lever = lever,
                actions = actions,
                performHaptic = performHaptic,
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
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.drive_step_value, lever.step),
            style = CabTheme.typography.speedValue,
            color = if (enabled) CabTheme.colorScheme.onSurface else CabTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 104.sp),
            modifier = Modifier.testTag(DriveTestTags.STEP),
        )
        Text(
            text = stringResource(R.string.drive_step_total, lever.stepCount),
            style = CabTheme.typography.speedUnit,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CabTheme.spacing.xs))
        DirectionReadout(loco.direction, directionLabel, stale)
        if (loco.speedStep == loco.speedStepCount) {
            Spacer(Modifier.height(CabTheme.spacing.xs))
            MaxBadge()
        }
        if (stale) {
            Spacer(Modifier.height(CabTheme.spacing.xs))
            Text(
                text = stringResource(R.string.drive_last_known),
                style = CabTheme.typography.speedCaption,
                color = CabTheme.extendedColors.caution,
            )
        }
        if (scale != null) {
            Spacer(Modifier.height(CabTheme.spacing.md))
            EstimatedSpeed(scale, lever.step.toFloat() / lever.stepCount)
        }
    }
}

@Composable
private fun DirectionReadout(
    direction: DriveDirection,
    label: String,
    stale: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector =
            if (direction == DriveDirection.Forward) {
                Icons.Filled.KeyboardArrowUp
            } else {
                Icons.Filled.KeyboardArrowDown
            },
            contentDescription = null,
            modifier = Modifier.size(CabTheme.sizing.iconMedium),
            tint = if (stale) CabTheme.extendedColors.caution else CabTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(CabTheme.spacing.xxs))
        Text(
            text = label.uppercase(),
            style = CabTheme.typography.status,
            color = if (stale) CabTheme.colorScheme.onSurfaceVariant else CabTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MaxBadge() {
    Surface(
        shape = CabTheme.shapes.pill,
        color = CabTheme.colorScheme.primaryContainer,
        contentColor = CabTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = stringResource(R.string.drive_max),
            style = CabTheme.typography.status,
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
    Text(
        text = text,
        style = CabTheme.typography.speedCaption,
        color = CabTheme.colorScheme.onSurfaceVariant,
        modifier =
        Modifier
            .testTag(DriveTestTags.ESTIMATE)
            .semanticsDescription(description),
    )
    Text(
        text = stringResource(R.string.drive_scale_caption),
        style = CabTheme.typography.speedCaption,
        color = CabTheme.colorScheme.onSurfaceVariant,
    )
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
    val handleHeightPx = with(density) { 56.dp.toPx() }
    val travel = (heightPx - handleHeightPx).coerceAtLeast(1f)
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

    Box(
        modifier =
        modifier
            .onSizeChanged { heightPx = it.height }
            .testTag(DriveTestTags.LEVER)
            .leverKeyboard(actions, performHaptic)
            .focusable()
            .leverSemantics(lever, enabled, labels, actions, performHaptic),
        contentAlignment = Alignment.TopCenter,
    ) {
        LeverTrack(
            fraction = lever.fraction,
            handleHeightPx = handleHeightPx,
            enabled = enabled,
        )
        LeverHandle(
            enabled = enabled,
            modifier =
            Modifier
                .testTag(DriveTestTags.HANDLE)
                .graphicsLayer {
                    translationY = (1f - lever.fraction) * travel
                }
                .leverDrag(lever, enabled, travel, actions, performHaptic),
        )
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
    val trackColor = CabTheme.colorScheme.surfaceVariant
    val activeColor = if (enabled) CabTheme.colorScheme.primary else CabTheme.colorScheme.outline
    val tickColor = CabTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val top = handleHeightPx / 2f
        val bottom = size.height - handleHeightPx / 2f
        val stroke = 10.dp.toPx()
        drawLine(
            color = trackColor,
            start = Offset(centerX, top),
            end = Offset(centerX, bottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val handleCenter = bottom - (bottom - top) * fraction
        drawLine(
            color = activeColor,
            start = Offset(centerX, handleCenter),
            end = Offset(centerX, bottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { tick ->
            val y = bottom - (bottom - top) * tick
            drawLine(
                color = tickColor,
                start = Offset(centerX - 16.dp.toPx(), y),
                end = Offset(centerX - 8.dp.toPx(), y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun LeverHandle(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(96.dp).height(56.dp),
        shape = CabTheme.shapes.control,
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
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) {
                    Surface(
                        modifier = Modifier.width(36.dp).height(2.dp),
                        shape = CircleShape,
                        color = LocalContentColor.current.copy(alpha = if (enabled) 0.8f else 0.4f),
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun NudgeButtons(
    enabled: Boolean,
    lever: LeverState,
    actions: DriveActions,
    performHaptic: (DriveHapticToken) -> Unit,
) {
    val upLabel = stringResource(R.string.drive_nudge_up)
    val downLabel = stringResource(R.string.drive_nudge_down)
    Column(
        verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NudgeButton(
            label = stringResource(R.string.drive_nudge_up_short),
            contentDescription = upLabel,
            enabled = enabled,
            onClick = {
                actions.onNudge(1)
                performHaptic(
                    if (lever.step + 1 >= lever.stepCount) {
                        DriveHapticToken.Limit
                    } else {
                        DriveHapticToken.Detent
                    },
                )
            },
            testTag = DriveTestTags.NUDGE_UP,
        )
        NudgeButton(
            label = stringResource(R.string.drive_nudge_down_short),
            contentDescription = downLabel,
            enabled = enabled,
            onClick = {
                actions.onNudge(-1)
                performHaptic(
                    if (lever.step - 1 <= 0) {
                        DriveHapticToken.Limit
                    } else {
                        DriveHapticToken.Detent
                    },
                )
            },
            testTag = DriveTestTags.NUDGE_DOWN,
        )
    }
}

@Composable
private fun NudgeButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier =
        Modifier
            .size(56.dp)
            .testTag(testTag)
            .semantics { if (!enabled) disabled() },
        shape = CabTheme.shapes.control,
        color =
        if (enabled) {
            CabTheme.colorScheme.surfaceContainerHighest
        } else {
            CabTheme.colorScheme.surfaceContainerLow
        },
        contentColor =
        if (enabled) {
            CabTheme.colorScheme.onSurface
        } else {
            CabTheme.extendedColors.disabledContent
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = CabTheme.typography.sectionTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { this.contentDescription = contentDescription },
            )
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
