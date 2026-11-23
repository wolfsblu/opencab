package dev.opencab.feature.throttle

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencab.core.designsystem.theme.CabElevationLevel
import dev.opencab.core.designsystem.theme.CabTheme

/** The two modes of the portrait instrument bay. */
enum class DriveInstrumentTab {
    Throttle,
    Functions,
}

/**
 * Compact-portrait instrument bay.
 *
 * Throttle and Functions are tabs over the same surface. The Direction/Stop deck is deliberately
 * pinned below both tabs, so switching to functions can never hide the stop control.
 */
@Suppress("LongParameterList")
@Composable
internal fun DrivePortraitInstrument(
    loco: DriveLoco,
    enabled: Boolean,
    scale: DriveScale?,
    haptics: DriveHaptics,
    actions: DriveActions,
    onReverseRequest: (DriveDirection) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: DriveInstrumentTab = DriveInstrumentTab.Throttle,
) {
    var tab by rememberSaveable(loco.address, initialTab) { mutableStateOf(initialTab) }
    Surface(
        modifier = modifier.fillMaxWidth().testTag(DriveTestTags.LEVER_PANEL),
        shape = CabTheme.shapes.panel,
        color = CabTheme.elevation.surfaceFor(
            level = CabElevationLevel.Raised,
            colorScheme = CabTheme.colorScheme,
        ),
        contentColor = CabTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DriveInstrumentTabRow(
                selected = tab,
                onSelect = { tab = it },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = CabTheme.spacing.sm,
                        end = CabTheme.spacing.sm,
                    ),
            )
            HorizontalDivider(color = CabTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Box(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = CabTheme.spacing.sm, vertical = CabTheme.spacing.xxs),
            ) {
                when (tab) {
                    DriveInstrumentTab.Throttle ->
                        DriveThrottleStage(
                            loco = loco,
                            enabled = enabled,
                            scale = scale,
                            haptics = haptics,
                            actions = actions,
                            modifier = Modifier.fillMaxSize(),
                        )

                    DriveInstrumentTab.Functions ->
                        DriveFunctionsGrid(
                            functions = loco.functions,
                            enabled = enabled,
                            actions = actions,
                            modifier = Modifier.fillMaxSize(),
                        )
                }
            }
            HorizontalDivider(color = CabTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
                    ),
            ) {
                DriveControlDeck(
                    loco = loco,
                    enabled = enabled,
                    haptics = haptics,
                    actions = actions,
                    onReverseRequest = onReverseRequest,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = CabTheme.spacing.page,
                            vertical = CabTheme.spacing.md,
                        )
                        .testTag(DriveTestTags.CONTROL_DECK),
                )
            }
        }
    }
}

@Composable
private fun DriveInstrumentTabRow(
    selected: DriveInstrumentTab,
    onSelect: (DriveInstrumentTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs),
    ) {
        DriveInstrumentTabItem(
            label = stringResource(R.string.drive_tab_throttle),
            selected = selected == DriveInstrumentTab.Throttle,
            onClick = { onSelect(DriveInstrumentTab.Throttle) },
            tag = DriveTestTags.THROTTLE_TAB,
            modifier = Modifier.weight(1f),
        )
        DriveInstrumentTabItem(
            label = stringResource(R.string.drive_tab_functions),
            selected = selected == DriveInstrumentTab.Functions,
            onClick = { onSelect(DriveInstrumentTab.Functions) },
            tag = DriveTestTags.FUNCTIONS_TAB,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DriveInstrumentTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue =
        if (selected) {
            CabTheme.colorScheme.primary
        } else {
            CabTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = CabTheme.motion.duration<Color>(durationMillis = 90),
        label = "instrumentTabColor",
    )
    Column(
        modifier =
        modifier
            .heightIn(min = CabTheme.sizing.touchMin)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag(tag)
            .padding(start = CabTheme.spacing.xs, end = CabTheme.spacing.xs, top = CabTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = CabTheme.typography.sectionTitle,
            color = contentColor,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 15.sp),
        )
        Box(
            modifier =
            Modifier
                .padding(top = CabTheme.spacing.xxs)
                .fillMaxWidth(0.55f)
                .height(3.dp)
                .background(
                    color = if (selected) contentColor else Color.Transparent,
                    shape = CabTheme.shapes.indicator,
                ),
        )
    }
}
