package dev.opencab.feature.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opencab.core.designsystem.component.CabScaffold
import dev.opencab.core.designsystem.component.EmptyState
import dev.opencab.core.designsystem.component.PrimaryActionButton
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabElevationLevel
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.core.model.RosterColor
import dev.opencab.core.model.RosterIcon

@Composable
fun RosterRoute(
    onAddLoco: () -> Unit,
    onEditLoco: (String) -> Unit,
    onOpenDrive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RosterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event == RosterEvent.OpenDrive) onOpenDrive()
        }
    }
    RosterScreen(
        uiState = uiState,
        onAddLoco = onAddLoco,
        onEditLoco = onEditLoco,
        onDriveLoco = viewModel::onDrive,
        onImportServerRoster = viewModel::onImportServerRoster,
        modifier = modifier,
    )
}

@Composable
fun RosterScreen(
    uiState: RosterUiState,
    modifier: Modifier = Modifier,
    onAddLoco: () -> Unit = {},
    onEditLoco: (String) -> Unit = {},
    onDriveLoco: (String) -> Unit = {},
    onImportServerRoster: () -> Unit = {},
) {
    CabScaffold(
        title = stringResource(R.string.roster_title),
        subtitle =
        if (uiState.isLoading) {
            null
        } else {
            stringResource(R.string.roster_subtitle, uiState.locos.size)
        },
        modifier = modifier.testTag(RosterTestTags.SCREEN),
        actions = {
            IconButton(
                onClick = onAddLoco,
                modifier = Modifier
                    .size(CabTheme.sizing.touchMin)
                    .testTag(RosterTestTags.ADD),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.roster_add_content_description),
                )
            }
        },
    ) { padding ->
        RosterContent(
            uiState = uiState,
            contentPadding = padding,
            onAddLoco = onAddLoco,
            onEditLoco = onEditLoco,
            onDriveLoco = onDriveLoco,
            onImportServerRoster = onImportServerRoster,
        )
    }
}

@Composable
private fun RosterContent(
    uiState: RosterUiState,
    contentPadding: PaddingValues,
    onAddLoco: () -> Unit,
    onEditLoco: (String) -> Unit,
    onDriveLoco: (String) -> Unit,
    onImportServerRoster: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag(RosterTestTags.LIST),
        contentPadding = PaddingValues(
            start = CabTheme.spacing.page,
            end = CabTheme.spacing.page,
            top = CabTheme.spacing.sm,
            bottom = CabTheme.spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
    ) {
        if (uiState.importEnabled && uiState.serverRosterCount > 0) {
            item(key = "import") {
                ImportRow(
                    count = uiState.serverRosterCount,
                    onClick = onImportServerRoster,
                )
            }
        }
        uiState.importedCount?.let { count ->
            item(key = "import-status") {
                ImportStatus(count)
            }
        }
        when {
            uiState.isLoading -> item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CabTheme.spacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.isEmpty -> item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.roster_empty_title),
                    message = stringResource(R.string.roster_empty_body),
                    modifier = Modifier.testTag(RosterTestTags.EMPTY),
                    action = {
                        PrimaryActionButton(
                            text = stringResource(R.string.roster_add_first),
                            onClick = onAddLoco,
                        )
                    },
                )
            }

            else -> items(items = uiState.locos, key = { it.id }) { loco ->
                RosterRow(
                    loco = loco,
                    onEdit = { onEditLoco(loco.id) },
                    onDrive = { onDriveLoco(loco.id) },
                )
            }
        }
    }
}

@Composable
private fun RosterRow(
    loco: RosterItemUi,
    onEdit: () -> Unit,
    onDrive: () -> Unit,
) {
    val driveLabel = stringResource(R.string.roster_drive_content_description, loco.name)
    Surface(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RosterTestTags.entry(loco.id)),
        shape = CabTheme.shapes.panel,
        color = CabTheme.elevation.surfaceFor(
            level = CabElevationLevel.Raised,
            colorScheme = CabTheme.colorScheme,
        ),
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
                        text = loco.name,
                        style = CabTheme.typography.sectionTitle,
                        color = CabTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (loco.isFavorite) {
                        Spacer(Modifier.width(CabTheme.spacing.xs))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = stringResource(
                                R.string.roster_favorite_content_description,
                                loco.name,
                            ),
                            modifier = Modifier.size(CabTheme.sizing.iconSmall),
                            tint = CabTheme.colorScheme.tertiary,
                        )
                    }
                }
                Text(
                    text = loco.addressDisplay,
                    style = CabTheme.typography.readoutSmall,
                    color = CabTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loco.isActive) {
                ActiveChip()
            }
            IconButton(
                onClick = onDrive,
                modifier = Modifier
                    .size(CabTheme.sizing.touchMin)
                    .testTag(RosterTestTags.drive(loco.id))
                    .semantics {
                        contentDescription = driveLabel
                    },
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = CabTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ImportRow(
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CabTheme.shapes.control,
        color = CabTheme.colorScheme.secondaryContainer,
        contentColor = CabTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RosterTestTags.IMPORT),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CabTheme.sizing.touchMin)
                .padding(horizontal = CabTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(CabTheme.sizing.iconMedium),
            )
            Spacer(Modifier.width(CabTheme.spacing.sm))
            Text(
                text = stringResource(R.string.roster_import, count),
                style = CabTheme.typography.sectionTitle,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ImportStatus(count: Int) {
    Text(
        text = stringResource(
            if (count > 0) R.string.roster_import_done else R.string.roster_import_none,
            count,
        ),
        style = CabTheme.typography.readoutSmall,
        color = CabTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RosterTestTags.IMPORT_STATUS)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@CabPreview
@Composable
private fun RosterScreenPreview() {
    CabPreviewSurface {
        RosterScreen(
            uiState = RosterUiState(
                isLoading = false,
                locos = listOf(
                    RosterItemUi(
                        id = "one",
                        name = "Black Five",
                        addressLabel = "L3412",
                        addressDisplay = "3412 · long",
                        isFavorite = true,
                        color = RosterColor.AMBER,
                        icon = RosterIcon.STEAM,
                        isActive = true,
                        lastUsedEpochMillis = 1L,
                    ),
                    RosterItemUi(
                        id = "two",
                        name = "Class 08",
                        addressLabel = "S3",
                        addressDisplay = "3 · short",
                        isFavorite = false,
                        color = RosterColor.TEAL,
                        icon = RosterIcon.SHUNTER,
                        isActive = false,
                        lastUsedEpochMillis = null,
                    ),
                ),
            ),
        )
    }
}
