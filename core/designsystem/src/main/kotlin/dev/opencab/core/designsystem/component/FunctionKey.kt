package dev.opencab.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.R
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabExtendedColors
import dev.opencab.core.designsystem.theme.CabTheme

enum class FunctionKeyState {
    Off,
    Active,
    Momentary,
    Disabled,
}

/**
 * A function key with four visual states and an explicit state description.
 *
 * Active keys are filled and carry a check mark, so state is never encoded by colour alone.
 * Latching keys expose checkbox semantics; momentary keys expose button semantics.
 *
 * @param contentDescription Optional accessible name such as "Function 3, Whistle". When
 *   omitted the visible label is used.
 * @param latching Whether the key is a checkbox or a momentary button.
 */
@Composable
fun FunctionKey(
    label: String,
    state: FunctionKeyState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    latching: Boolean = true,
) {
    val colors = CabTheme.colorScheme
    val tones = functionKeyColors(state, colors, CabTheme.extendedColors)
    val enabled = state != FunctionKeyState.Disabled
    val shape = CabTheme.shapes.control
    val container by animateColorAsState(
        targetValue = tones.container,
        animationSpec = CabTheme.motion.duration(durationMillis = 90),
        label = "functionKeyContainer",
    )
    val stateLabel = functionKeyStateLabel(state)
    val interactionModifier =
        Modifier.functionKeyInteraction(
            latching = latching,
            selected = state == FunctionKeyState.Active,
            enabled = enabled,
            onClick = onClick,
        )

    Box(
        modifier =
        modifier
            .defaultMinSize(minWidth = CabTheme.sizing.functionKey, minHeight = CabTheme.sizing.functionKey)
            .clip(shape)
            .background(container)
            .border(width = 1.dp, color = tones.border, shape = shape)
            .semantics(mergeDescendants = true) {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                stateDescription = stateLabel
            }
            .then(interactionModifier)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CabTheme.typography.functionKey,
            color = tones.content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = if (contentDescription != null) Modifier.clearAndSetSemantics {} else Modifier,
        )
        if (state == FunctionKeyState.Active) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).size(12.dp),
                tint = tones.content,
            )
        }
    }
}

private data class FunctionKeyColors(
    val container: Color,
    val content: Color,
    val border: Color,
)

private fun functionKeyColors(
    state: FunctionKeyState,
    colors: ColorScheme,
    extended: CabExtendedColors,
): FunctionKeyColors = when (state) {
    FunctionKeyState.Off ->
        FunctionKeyColors(
            container = colors.surfaceContainerLow,
            content = colors.onSurface,
            border = colors.outlineVariant,
        )

    FunctionKeyState.Active ->
        FunctionKeyColors(
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
            border = Color.Transparent,
        )

    FunctionKeyState.Momentary ->
        FunctionKeyColors(
            container = colors.secondaryContainer,
            content = colors.onSecondaryContainer,
            border = Color.Transparent,
        )

    FunctionKeyState.Disabled ->
        FunctionKeyColors(
            container = colors.surfaceContainerLow.copy(alpha = 0.5f),
            content = extended.disabledContent,
            border = colors.outlineVariant.copy(alpha = 0.5f),
        )
}

private fun Modifier.functionKeyInteraction(
    latching: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier = if (latching) {
    selectable(
        selected = selected,
        enabled = enabled,
        role = Role.Checkbox,
        onClick = onClick,
    )
} else {
    clickable(
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
    )
}

@Composable
private fun functionKeyStateLabel(state: FunctionKeyState): String = stringResource(
    when (state) {
        FunctionKeyState.Off -> R.string.cab_function_off
        FunctionKeyState.Active -> R.string.cab_function_on
        FunctionKeyState.Momentary -> R.string.cab_function_momentary
        FunctionKeyState.Disabled -> R.string.cab_function_disabled
    },
)

@CabPreview
@Composable
private fun FunctionKeyPreview() {
    CabPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FunctionKey(label = "F0", state = FunctionKeyState.Off, onClick = {})
            FunctionKey(label = "F1", state = FunctionKeyState.Active, onClick = {})
            FunctionKey(label = "F2", state = FunctionKeyState.Momentary, onClick = {}, latching = false)
            FunctionKey(label = "F3", state = FunctionKeyState.Disabled, onClick = {})
        }
    }
}
