package dev.opencab.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.R
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabElevationLevel
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * A modal confirmation sheet for one consequential decision.
 *
 * The sheet inherits Material 3's predictive-back behaviour. The caller owns visibility;
 * dismissing is always a cancel, never an implicit confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun ConfirmSheet(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = stringResource(R.string.cab_cancel),
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = CabTheme.shapes.sheet,
        containerColor = CabTheme.elevation.surfaceFor(
            level = CabElevationLevel.Modal,
            colorScheme = CabTheme.colorScheme,
        ),
        contentColor = CabTheme.colorScheme.onSurface,
    ) {
        ConfirmSheetContent(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onConfirm = onConfirm,
            onDismiss = onDismissRequest,
            destructive = destructive,
            icon = icon,
        )
    }
}

/**
 * The inner content of [ConfirmSheet], exposed so a caller can compose it in a custom
 * container or a preview without creating a window-backed sheet.
 */
@Suppress("LongParameterList")
@Composable
fun ConfirmSheetContent(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = stringResource(R.string.cab_cancel),
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = CabTheme.spacing.lg)
            .padding(bottom = CabTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(CabTheme.sizing.iconMedium),
                    tint =
                    if (destructive) {
                        CabTheme.colorScheme.error
                    } else {
                        CabTheme.colorScheme.primary
                    },
                )
                Spacer(Modifier.width(CabTheme.spacing.sm))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = CabTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(
            text = message,
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm, Alignment.End),
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = CabTheme.sizing.touchMin),
            ) {
                Text(dismissLabel)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = CabTheme.sizing.touchMin),
                colors =
                if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = CabTheme.colorScheme.error,
                        contentColor = CabTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmLabel)
            }
        }
    }
}

@CabPreview
@Composable
private fun ConfirmSheetPreview() {
    CabPreviewSurface {
        Surface(
            shape = CabTheme.shapes.sheet,
            color = CabTheme.elevation.surfaceFor(
                level = CabElevationLevel.Modal,
                colorScheme = CabTheme.colorScheme,
            ),
        ) {
            ConfirmSheetContent(
                title = "Release 3412?",
                message = "Stops the train, then frees the address.",
                confirmLabel = "Release",
                onConfirm = {},
                onDismiss = {},
                destructive = true,
                dismissLabel = "Keep driving",
            )
        }
    }
}
