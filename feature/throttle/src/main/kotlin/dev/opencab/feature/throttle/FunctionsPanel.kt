package dev.opencab.feature.throttle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.component.FunctionKey
import dev.opencab.core.designsystem.component.FunctionKeyState
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * The functions pane used by wide layouts. It is deliberately flat and heading-free so it never
 * competes with the throttle. On compact portrait the same grid is shown inside the Functions tab.
 */
@Composable
internal fun DriveFunctionsPanel(
    functions: List<DriveFunction>,
    enabled: Boolean,
    actions: DriveActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CabTheme.shapes.panel,
        color = CabTheme.colorScheme.surface,
        contentColor = CabTheme.colorScheme.onSurface,
    ) {
        DriveFunctionsGrid(
            functions = functions,
            enabled = enabled,
            actions = actions,
            modifier = Modifier.fillMaxSize().padding(CabTheme.spacing.xs),
        )
    }
}

/** The scrollable function grid, shared by the wide pane and the portrait Functions tab. */
@Composable
internal fun DriveFunctionsGrid(
    functions: List<DriveFunction>,
    enabled: Boolean,
    actions: DriveActions,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    val cellMinSize = (72.dp * fontScale.coerceIn(1f, 1.8f)).coerceAtMost(160.dp)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = cellMinSize),
        modifier =
        modifier
            .fillMaxWidth()
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
