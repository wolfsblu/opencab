package dev.opencab.feature.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import dev.opencab.core.designsystem.component.EmptyState
import dev.opencab.core.designsystem.component.InlineError
import dev.opencab.core.designsystem.component.PrimaryActionButton
import dev.opencab.core.designsystem.component.SecondaryActionButton
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabElevationLevel
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.core.model.AddressValidation
import dev.opencab.core.model.AddressValidationError
import dev.opencab.core.model.RosterAddress
import dev.opencab.core.model.RosterAddressType
import dev.opencab.core.model.RosterColor
import dev.opencab.core.model.RosterIcon
import dev.opencab.core.model.RosterLoco

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocoPickerSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocoPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val baseActions = viewModel.actions
    val dismiss = rememberUpdatedState(onDismissRequest)
    val actions =
        remember(baseActions) {
            object : LocoPickerActions by baseActions {
                override fun onDismiss() {
                    dismiss.value()
                }
            }
        }
    LaunchedEffect(viewModel) {
        viewModel.reset()
        viewModel.eventFlow.collect { onDismissRequest() }
    }
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
        LocoPickerSheetContent(
            uiState = uiState,
            actions = actions,
        )
    }
}

@Composable
internal fun LocoPickerSheetContent(
    uiState: LocoPickerUiState,
    actions: LocoPickerActions = LocoPickerActions.None,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = CabTheme.spacing.page)
            .padding(bottom = CabTheme.spacing.md)
            .testTag(RosterTestTags.PICKER_SHEET),
        verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    if (uiState.mode == PickerMode.AddAddress) {
                        R.string.picker_add_title
                    } else {
                        R.string.picker_title
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = CabTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(
                onClick = actions::onDismiss,
                modifier = Modifier
                    .size(CabTheme.sizing.touchMin)
                    .testTag(RosterTestTags.PICKER_CLOSE),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.picker_close),
                )
            }
        }

        if (uiState.mode == PickerMode.AddAddress) {
            PickerAddFormContent(
                uiState = uiState,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
        } else {
            PickerList(
                uiState = uiState,
                actions = actions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PickerList(
    uiState: LocoPickerUiState,
    actions: LocoPickerActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = actions::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RosterTestTags.PICKER_SEARCH),
            label = { Text(stringResource(R.string.picker_search_label)) },
            placeholder = { Text(stringResource(R.string.picker_search_placeholder)) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(CabTheme.sizing.iconMedium),
                )
            },
        )

        Surface(
            onClick = { actions.onStartAdd(uiState.query.takeIf { it.isNotBlank() }) },
            shape = CabTheme.shapes.control,
            color = CabTheme.colorScheme.secondaryContainer,
            contentColor = CabTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RosterTestTags.PICKER_ADD),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = CabTheme.sizing.touchMin)
                    .padding(horizontal = CabTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(CabTheme.sizing.iconMedium),
                )
                Spacer(Modifier.width(CabTheme.spacing.sm))
                Text(
                    text = stringResource(R.string.picker_add_address),
                    style = CabTheme.typography.sectionTitle,
                )
            }
        }

        PickerResults(
            uiState = uiState,
            actions = actions,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PickerResults(
    uiState: LocoPickerUiState,
    actions: LocoPickerActions,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isEmpty -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = stringResource(R.string.picker_empty_title),
                message = stringResource(R.string.picker_empty_body),
                modifier = Modifier.testTag(RosterTestTags.PICKER_EMPTY),
                action = {
                    PrimaryActionButton(
                        text = stringResource(R.string.picker_add_first),
                        onClick = { actions.onStartAdd(null) },
                    )
                },
            )
        }

        uiState.sections.isEmpty() -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = stringResource(R.string.picker_no_matches),
                message = stringResource(R.string.picker_no_matches_body),
                action = {
                    PrimaryActionButton(
                        text = stringResource(R.string.picker_add_address),
                        onClick = { actions.onStartAdd(uiState.query) },
                    )
                },
            )
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs),
        ) {
            uiState.sections.forEach { section ->
                item(key = "header-${section.kind}") {
                    Text(
                        text = stringResource(section.kind.title()),
                        style = CabTheme.typography.sectionTitle,
                        color = CabTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = CabTheme.spacing.xs)
                            .semantics { heading() },
                    )
                }
                items(items = section.locos, key = { it.id }) { loco ->
                    PickerRow(
                        loco = loco,
                        isActive = loco.id == uiState.activeLocoId,
                        onClick = { actions.onSelect(loco) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    loco: RosterLoco,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CabTheme.shapes.control,
        color = CabTheme.elevation.surfaceFor(
            level = CabElevationLevel.Raised,
            colorScheme = CabTheme.colorScheme,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RosterTestTags.pickerEntry(loco.id)),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = CabTheme.spacing.md,
                vertical = CabTheme.spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
        ) {
            LocoBadge(color = loco.color, icon = loco.icon)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = loco.displayName,
                        style = CabTheme.typography.sectionTitle,
                        color = CabTheme.colorScheme.onSurface,
                    )
                    if (loco.isFavorite) {
                        Spacer(Modifier.width(CabTheme.spacing.xs))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = stringResource(
                                R.string.roster_favorite_content_description,
                                loco.displayName,
                            ),
                            modifier = Modifier.size(CabTheme.sizing.iconSmall),
                            tint = CabTheme.colorScheme.tertiary,
                        )
                    }
                }
                Text(
                    text = loco.address.displayText,
                    style = CabTheme.typography.readoutSmall,
                    color = CabTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isActive) ActiveChip()
        }
    }
}

@Composable
private fun PickerAddFormContent(
    uiState: LocoPickerUiState,
    actions: LocoPickerActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag(RosterTestTags.PICKER_ADD_FORM),
        verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.md),
    ) {
        OutlinedTextField(
            value = uiState.form.name,
            onValueChange = actions::onAddNameChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RosterTestTags.PICKER_ADD_NAME),
            label = { Text(stringResource(R.string.picker_name)) },
            placeholder = {
                Text(stringResource(R.string.picker_name_placeholder, uiState.form.address.ifBlank { "—" }))
            },
            singleLine = true,
        )
        OutlinedTextField(
            value = uiState.form.address,
            onValueChange = actions::onAddAddressChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RosterTestTags.PICKER_ADD_ADDRESS),
            label = { Text(stringResource(R.string.picker_address)) },
            placeholder = { Text(stringResource(R.string.picker_address_hint)) },
            singleLine = true,
            isError = uiState.addError is PickerAddError.InvalidAddress,
            supportingText = {
                Text(
                    addressSupportingText(
                        validation = uiState.validation,
                        showError = uiState.addError is PickerAddError.InvalidAddress,
                    ),
                )
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number,
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs)) {
            Text(
                text = stringResource(R.string.editor_type),
                style = CabTheme.typography.sectionTitle,
                color = CabTheme.colorScheme.onSurfaceVariant,
            )
            AddressTypeChips(
                selected = uiState.form.type,
                onSelect = actions::onAddTypeChange,
            )
        }
        DuplicateAddressError(uiState.addError, actions::onUseExisting)
        PickerAddButtons(
            isSaving = uiState.isSaving,
            onCancel = actions::onCancelAdd,
            onSubmit = actions::onSubmitAdd,
        )
    }
}

@Composable
private fun DuplicateAddressError(
    addError: PickerAddError?,
    onUseExisting: () -> Unit,
) {
    val duplicate = addError as? PickerAddError.Duplicate ?: return
    InlineError(
        title = stringResource(R.string.picker_duplicate_title),
        message = stringResource(R.string.picker_duplicate, duplicate.existing.displayName),
        onRetry = onUseExisting,
        retryLabel = stringResource(R.string.picker_use_existing),
        modifier = Modifier.testTag(RosterTestTags.PICKER_ADD_ERROR),
    )
}

@Composable
private fun PickerAddButtons(
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm, Alignment.End),
    ) {
        SecondaryActionButton(
            text = stringResource(R.string.picker_cancel),
            onClick = onCancel,
            modifier = Modifier.testTag(RosterTestTags.PICKER_ADD_CANCEL),
        )
        PrimaryActionButton(
            text = stringResource(R.string.picker_add_and_drive),
            onClick = onSubmit,
            loading = isSaving,
            modifier = Modifier.testTag(RosterTestTags.PICKER_ADD_SAVE),
        )
    }
}

private fun PickerSectionKind.title(): Int = when (this) {
    PickerSectionKind.Favorites -> R.string.picker_section_favorites
    PickerSectionKind.Recents -> R.string.picker_section_recents
    PickerSectionKind.All -> R.string.picker_section_all
    PickerSectionKind.Matches -> R.string.picker_section_matches
}

@CabPreview
@Composable
private fun LocoPickerPreview() {
    CabPreviewSurface {
        LocoPickerSheetContent(
            uiState = LocoPickerUiState(
                allLocos = previewLocos(),
                sections = RosterSearch.sections(previewLocos(), ""),
                activeLocoId = "one",
                validation = AddressValidation.Invalid(
                    AddressValidationError.REQUIRED,
                ),
            ),
            actions = LocoPickerActions.None,
            modifier = Modifier.padding(top = CabTheme.spacing.md),
        )
    }
}

private fun previewLocos(): List<RosterLoco> = listOf(
    RosterLoco(
        id = "one",
        name = "Black Five",
        address = RosterAddress(3412, RosterAddressType.LONG),
        isFavorite = true,
        color = RosterColor.AMBER,
        icon = RosterIcon.STEAM,
        lastUsedEpochMillis = 2_000L,
    ),
    RosterLoco(
        id = "two",
        name = "Class 08",
        address = RosterAddress(3, RosterAddressType.SHORT),
        color = RosterColor.TEAL,
        icon = RosterIcon.SHUNTER,
        lastUsedEpochMillis = 1_000L,
    ),
)
