package dev.opencab.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import dev.opencab.core.designsystem.component.SecondaryActionButton
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabElevationLevel
import dev.opencab.core.designsystem.theme.CabTheme

/** Window-backed manual-entry sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualEntrySheet(
    state: ManualEntryUiState,
    onNameChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = CabTheme.shapes.sheet,
        containerColor = CabTheme.elevation.surfaceFor(
            level = CabElevationLevel.Modal,
            colorScheme = CabTheme.colorScheme,
        ),
        contentColor = CabTheme.colorScheme.onSurface,
    ) {
        ManualEntrySheetContent(
            state = state,
            onNameChange = onNameChange,
            onHostChange = onHostChange,
            onPortChange = onPortChange,
            onSubmit = onSubmit,
            onDismiss = onDismiss,
        )
    }
}

/** Inner content, exposed for a preview and for UI tests without a window-backed sheet. */
@Suppress("LongMethod")
@Composable
internal fun ManualEntrySheetContent(
    state: ManualEntryUiState,
    onNameChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = CabTheme.spacing.lg)
            .padding(bottom = CabTheme.spacing.lg)
            .verticalScroll(rememberScrollState())
            .testTag(ConnectionTestTags.MANUAL_SHEET),
        verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.md),
    ) {
        Text(
            text = stringResource(
                if (state.isEditing) {
                    R.string.connection_manual_edit_title
                } else {
                    R.string.connection_manual_title
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = CabTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.connection_manual_name)) },
            placeholder = { Text(stringResource(R.string.connection_manual_name_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )

        OutlinedTextField(
            value = state.host,
            onValueChange = onHostChange,
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ConnectionTestTags.MANUAL_HOST),
            label = { Text(stringResource(R.string.connection_manual_host)) },
            placeholder = { Text(stringResource(R.string.connection_manual_host_hint)) },
            singleLine = true,
            isError = state.hostError != null,
            supportingText = {
                val error = state.hostError
                if (error != null) {
                    Text(stringResource(error.hostMessage()))
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
        )

        OutlinedTextField(
            value = state.port,
            onValueChange = onPortChange,
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ConnectionTestTags.MANUAL_PORT),
            label = { Text(stringResource(R.string.connection_manual_port)) },
            placeholder = { Text(stringResource(R.string.connection_manual_port_hint)) },
            singleLine = true,
            isError = state.portError != null,
            supportingText = {
                val error = state.portError
                Text(
                    text = if (error != null) {
                        stringResource(error.portMessage())
                    } else {
                        stringResource(R.string.connection_manual_default_port)
                    },
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryActionButton(
                text = stringResource(R.string.connection_manual_cancel),
                onClick = onDismiss,
            )
            Button(
                onClick = onSubmit,
                modifier =
                Modifier
                    .heightIn(min = CabTheme.sizing.touchMin)
                    .testTag(ConnectionTestTags.MANUAL_SUBMIT),
                shape = CabTheme.shapes.pill,
            ) {
                Text(
                    stringResource(
                        if (state.isEditing) {
                            R.string.connection_manual_save
                        } else {
                            R.string.connection_manual_connect
                        },
                    ),
                )
            }
        }
    }
}

private fun ManualFieldError.hostMessage(): Int = when (this) {
    ManualFieldError.Required -> R.string.connection_manual_host_required
    ManualFieldError.InvalidHost -> R.string.connection_manual_host_invalid
    ManualFieldError.InvalidPort -> R.string.connection_manual_port_invalid
}

private fun ManualFieldError.portMessage(): Int = when (this) {
    ManualFieldError.Required -> R.string.connection_manual_port_required
    ManualFieldError.InvalidPort -> R.string.connection_manual_port_invalid
    ManualFieldError.InvalidHost -> R.string.connection_manual_host_invalid
}

@CabPreview
@Composable
private fun ManualEntrySheetPreview() {
    CabPreviewSurface {
        Surface(
            shape = CabTheme.shapes.sheet,
            color = CabTheme.elevation.surfaceFor(
                level = CabElevationLevel.Modal,
                colorScheme = CabTheme.colorScheme,
            ),
        ) {
            ManualEntrySheetContent(
                state = ManualEntryUiState(
                    host = "192.168.1.24",
                    hostError = ManualFieldError.InvalidHost,
                    port = "12090",
                ),
                onNameChange = {},
                onHostChange = {},
                onPortChange = {},
                onSubmit = {},
                onDismiss = {},
                modifier = Modifier.padding(top = CabTheme.spacing.md),
            )
        }
    }
}
