package dev.opencab.feature.throttle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.component.FunctionKey
import dev.opencab.core.designsystem.component.FunctionKeyState
import dev.opencab.core.designsystem.theme.CabElevationLevel
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * The function grid.
 *
 * Portrait expands it below the driving controls; wide layouts keep it permanently beside the
 * lever. Only the grid scrolls, so Stop, Direction, and nudge never leave the screen.
 */
@Composable
internal fun DriveFunctionsPanel(
    functions: List<DriveFunction>,
    enabled: Boolean,
    actions: DriveActions,
    modifier: Modifier = Modifier,
    onToggleExpanded: (() -> Unit)? = null,
    expanded: Boolean = true,
) {
    Surface(
        modifier = modifier,
        shape = CabTheme.shapes.panel,
        color = CabTheme.elevation.surfaceFor(
            level = CabElevationLevel.Raised,
            colorScheme = CabTheme.colorScheme,
        ),
        contentColor = CabTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(CabTheme.spacing.sm)) {
            FunctionsHeader(
                functions = functions,
                expanded = expanded,
                onToggleExpanded = onToggleExpanded,
            )
            if (expanded) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 72.dp),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag(DriveTestTags.FUNCTIONS),
                    contentPadding = PaddingValues(vertical = CabTheme.spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs),
                ) {
                    items(
                        items = functions,
                        key = { function -> function.number },
                    ) { function ->
                        FunctionKeyForDrive(
                            function = function,
                            enabled = enabled,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FunctionsHeader(
    functions: List<DriveFunction>,
    expanded: Boolean,
    onToggleExpanded: (() -> Unit)?,
) {
    val activeCount = functions.count { it.isOn }
    val title =
        if (activeCount > 0) {
            stringResource(R.string.drive_functions_count, activeCount)
        } else {
            stringResource(R.string.drive_functions)
        }
    val description =
        if (expanded) {
            stringResource(R.string.drive_functions_hide)
        } else {
            stringResource(R.string.drive_functions_show)
        }
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = CabTheme.sizing.touchMin)
            .then(
                if (onToggleExpanded != null) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onToggleExpanded)
                        .testTag(DriveTestTags.FUNCTIONS_TOGGLE)
                        .semantics(mergeDescendants = true) {}
                } else {
                    Modifier
                },
            )
            .padding(horizontal = CabTheme.spacing.xs, vertical = CabTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = CabTheme.typography.sectionTitle,
            color = CabTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onToggleExpanded != null) {
            Icon(
                imageVector =
                if (expanded) {
                    Icons.Filled.KeyboardArrowDown
                } else {
                    Icons.Filled.KeyboardArrowUp
                },
                contentDescription = description,
                modifier = Modifier.size(CabTheme.sizing.iconMedium),
                tint = CabTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FunctionKeyForDrive(
    function: DriveFunction,
    enabled: Boolean,
    actions: DriveActions,
) {
    val keyEnabled = enabled && function.isEnabled
    val state = when {
        !keyEnabled -> FunctionKeyState.Disabled
        function.isOn -> FunctionKeyState.Active
        function.isMomentary -> FunctionKeyState.Momentary
        else -> FunctionKeyState.Off
    }
    val description =
        stringResource(R.string.drive_function_description, function.number, function.label)
    val holdStart = if (function.isMomentary && keyEnabled) {
        { actions.onFunctionHoldStart(function.number) }
    } else {
        null
    }
    val holdEnd = if (function.isMomentary && keyEnabled) {
        { actions.onFunctionHoldEnd(function.number) }
    } else {
        null
    }
    FunctionKey(
        label = function.label,
        state = state,
        onClick = { actions.onToggleFunction(function.number) },
        contentDescription = description,
        latching = !function.isMomentary,
        onPressStart = holdStart,
        onPressEnd = holdEnd,
        modifier = Modifier.testTag(DriveTestTags.function(function.number)),
    )
}
