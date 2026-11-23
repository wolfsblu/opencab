package dev.opencab.feature.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencab.core.designsystem.component.CabScaffold
import dev.opencab.core.designsystem.component.InlineError
import dev.opencab.core.designsystem.component.PrimaryActionButton
import dev.opencab.core.designsystem.component.SecondaryActionButton
import dev.opencab.core.designsystem.component.SectionCard
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.core.model.AddressValidation
import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.model.RosterColor
import dev.opencab.core.model.RosterIcon

@Composable
fun LocoEditorRoute(
    onBack: () -> Unit,
    onSaved: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocoEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is EditorEvent.Saved -> onSaved(event.locoId, event.createdNewEntry)
            }
        }
    }
    val actions: LocoEditorActions = viewModel.actions
    LocoEditorScreen(
        uiState = uiState,
        actions = actions,
        onBack = onBack,
        modifier = modifier,
    )
}

/** The editor's action surface. A test or preview can override only the callback it exercises. */
@Suppress("TooManyFunctions")
interface LocoEditorActions {
    fun onNameChange(value: String) = Unit

    fun onAddressChange(value: String) = Unit

    fun onTypeChange(type: RosterAddressType?) = Unit

    fun onFavoriteChange(value: Boolean) = Unit

    fun onColorChange(color: RosterColor) = Unit

    fun onIconChange(icon: RosterIcon) = Unit

    fun onFunctionLabelChange(
        number: Int,
        value: String,
    ) = Unit

    fun onSave() = Unit

    companion object {
        val None: LocoEditorActions = object : LocoEditorActions {}
    }
}

@Composable
fun LocoEditorScreen(
    uiState: LocoEditorUiState,
    onBack: () -> Unit,
    actions: LocoEditorActions = LocoEditorActions.None,
    modifier: Modifier = Modifier,
) {
    val title = if (uiState.isNew) {
        stringResource(R.string.editor_new_title)
    } else {
        stringResource(R.string.editor_edit_title, uiState.name)
    }
    CabScaffold(
        title = title,
        modifier = modifier.testTag(RosterTestTags.EDITOR_SCREEN),
        onBack = onBack,
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@CabScaffold
        }
        if (uiState.saveError == EditorSaveError.NOT_FOUND) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(CabTheme.spacing.page),
            ) {
                InlineError(
                    title = stringResource(R.string.editor_error_not_found),
                    message = stringResource(R.string.editor_error_not_found),
                )
            }
            return@CabScaffold
        }
        EditorForm(
            uiState = uiState,
            actions = actions,
            contentPadding = padding,
        )
    }
}

@Composable
private fun EditorForm(
    uiState: LocoEditorUiState,
    actions: LocoEditorActions,
    contentPadding: PaddingValues,
) {
    var labelsExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = CabTheme.spacing.page, vertical = CabTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.md),
    ) {
        EditorNameField(uiState, actions)
        EditorAddressSection(uiState, actions)
        EditorFavoriteRow(uiState, actions)
        EditorColorSection(uiState, actions)
        EditorIconSection(uiState, actions)
        EditorFunctionsSection(
            uiState = uiState,
            expanded = labelsExpanded,
            onToggleExpanded = { labelsExpanded = !labelsExpanded },
            onLabelChange = actions::onFunctionLabelChange,
        )
        PrimaryActionButton(
            text = stringResource(R.string.editor_save),
            onClick = actions::onSave,
            loading = uiState.isSaving,
            enabled = uiState.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RosterTestTags.EDITOR_SAVE),
        )
        Spacer(Modifier.height(CabTheme.spacing.xl))
    }
}

@Composable
private fun EditorNameField(
    uiState: LocoEditorUiState,
    actions: LocoEditorActions,
) {
    OutlinedTextField(
        value = uiState.name,
        onValueChange = actions::onNameChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RosterTestTags.EDITOR_NAME),
        label = { Text(stringResource(R.string.editor_name)) },
        singleLine = true,
        isError = uiState.showErrors && uiState.name.isBlank(),
        supportingText = if (uiState.showErrors && uiState.name.isBlank()) {
            { Text(stringResource(R.string.editor_name_required)) }
        } else {
            null
        },
    )
}

@Composable
private fun EditorAddressSection(
    uiState: LocoEditorUiState,
    actions: LocoEditorActions,
) {
    SectionCard(title = stringResource(R.string.editor_address)) {
        OutlinedTextField(
            value = uiState.address,
            onValueChange = actions::onAddressChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RosterTestTags.EDITOR_ADDRESS),
            label = { Text(stringResource(R.string.picker_address)) },
            singleLine = true,
            isError = uiState.showErrors && uiState.validation !is AddressValidation.Valid,
            supportingText = {
                Text(
                    addressSupportingText(
                        validation = uiState.validation,
                        showError = uiState.showErrors,
                    ),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Text(
            text = stringResource(R.string.editor_type),
            style = CabTheme.typography.sectionTitle,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
        AddressTypeChips(
            selected = uiState.type,
            onSelect = actions::onTypeChange,
        )
        if (uiState.addressChanged) {
            Text(
                text = stringResource(R.string.editor_address_changed),
                style = CabTheme.typography.readoutSmall,
                color = CabTheme.colorScheme.tertiary,
                modifier = Modifier
                    .testTag(RosterTestTags.EDITOR_CHANGE_NOTE)
                    .semantics { heading() },
            )
        }
        val duplicate = uiState.duplicateName
        if (duplicate != null) {
            InlineError(
                title = stringResource(R.string.picker_duplicate_title),
                message = stringResource(R.string.editor_duplicate, duplicate),
                modifier = Modifier.testTag(RosterTestTags.EDITOR_ERROR),
            )
        }
    }
}

@Composable
private fun EditorFavoriteRow(
    uiState: LocoEditorUiState,
    actions: LocoEditorActions,
) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CabTheme.sizing.touchMin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.editor_favorite),
                style = CabTheme.typography.sectionTitle,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = uiState.isFavorite,
                onCheckedChange = actions::onFavoriteChange,
                modifier = Modifier.testTag(RosterTestTags.EDITOR_FAVORITE),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorColorSection(
    uiState: LocoEditorUiState,
    actions: LocoEditorActions,
) {
    SectionCard(title = stringResource(R.string.editor_color)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs)) {
            RosterColor.entries.forEach { color ->
                FilterChip(
                    selected = uiState.color == color,
                    onClick = { actions.onColorChange(color) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = color.toComposeColor(),
                                modifier = Modifier.size(14.dp),
                                content = {},
                            )
                            Spacer(Modifier.width(CabTheme.spacing.xs))
                            Text(colorLabel(color))
                        }
                    },
                    modifier = Modifier.testTag(RosterTestTags.color(color.key)),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorIconSection(
    uiState: LocoEditorUiState,
    actions: LocoEditorActions,
) {
    SectionCard(title = stringResource(R.string.editor_icon)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs)) {
            RosterIcon.entries.forEach { icon ->
                val tint = uiState.color.toComposeColor()
                FilterChip(
                    selected = uiState.icon == icon,
                    onClick = { actions.onIconChange(icon) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon.imageVector(),
                                contentDescription = null,
                                modifier = Modifier.size(CabTheme.sizing.iconSmall),
                                tint = tint,
                            )
                            Spacer(Modifier.width(CabTheme.spacing.xs))
                            Text(iconLabel(icon))
                        }
                    },
                    modifier = Modifier.testTag(RosterTestTags.icon(icon.key)),
                )
            }
        }
    }
}

@Composable
private fun EditorFunctionsSection(
    uiState: LocoEditorUiState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onLabelChange: (Int, String) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.editor_functions),
        subtitle = stringResource(R.string.editor_functions_body),
    ) {
        SecondaryActionButton(
            text = stringResource(
                if (expanded) R.string.editor_functions_hide else R.string.editor_functions_show,
            ),
            onClick = onToggleExpanded,
            modifier = Modifier.testTag(RosterTestTags.EDITOR_FUNCTIONS_TOGGLE),
        )
        if (expanded) {
            for (number in 0..28) {
                FunctionLabelField(
                    number = number,
                    localLabel = uiState.functionLabels[number].orEmpty(),
                    serverLabel = uiState.serverFunctionLabels[number],
                    onChange = { onLabelChange(number, it) },
                )
            }
        }
    }
}

@Composable
private fun FunctionLabelField(
    number: Int,
    localLabel: String,
    serverLabel: String?,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = localLabel,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RosterTestTags.functionLabel(number)),
        label = { Text("F$number") },
        placeholder = serverLabel?.let { { Text(stringResource(R.string.editor_function_placeholder, it)) } },
        singleLine = true,
    )
}

@Composable
private fun colorLabel(color: RosterColor): String = stringResource(
    when (color) {
        RosterColor.BLUE -> R.string.roster_color_blue
        RosterColor.TEAL -> R.string.roster_color_teal
        RosterColor.GREEN -> R.string.roster_color_green
        RosterColor.AMBER -> R.string.roster_color_amber
        RosterColor.RED -> R.string.roster_color_red
        RosterColor.VIOLET -> R.string.roster_color_violet
    },
)

@Composable
private fun iconLabel(icon: RosterIcon): String = stringResource(
    when (icon) {
        RosterIcon.STEAM -> R.string.roster_icon_steam
        RosterIcon.DIESEL -> R.string.roster_icon_diesel
        RosterIcon.ELECTRIC -> R.string.roster_icon_electric
        RosterIcon.SHUNTER -> R.string.roster_icon_shunter
    },
)

@CabPreview
@Composable
private fun LocoEditorPreview() {
    CabPreviewSurface {
        LocoEditorScreen(
            uiState = LocoEditorUiState(
                isLoading = false,
                isNew = false,
                name = "Black Five",
                address = "3412",
                validation = AddressValidation.Valid(
                    address = RosterAddress(3412, RosterAddressType.LONG),
                    isAmbiguous = false,
                ),
                isFavorite = true,
                color = RosterColor.AMBER,
                icon = RosterIcon.STEAM,
                functionLabels = mapOf(0 to "Headlight"),
                serverFunctionLabels = mapOf(1 to "Whistle", 2 to "Bell"),
            ),
            onBack = {},
        )
    }
}
