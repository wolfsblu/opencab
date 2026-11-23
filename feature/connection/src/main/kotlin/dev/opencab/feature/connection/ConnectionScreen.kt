package dev.opencab.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.component.CabConnectionStatus
import dev.opencab.core.designsystem.component.CabScaffold
import dev.opencab.core.designsystem.component.EmptyState
import dev.opencab.core.designsystem.component.InlineError
import dev.opencab.core.designsystem.component.PrimaryActionButton
import dev.opencab.core.designsystem.component.PrimaryActionSize
import dev.opencab.core.designsystem.component.SectionCard
import dev.opencab.core.designsystem.component.StatusPill
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.core.model.ConnectionError
import dev.opencab.core.model.ConnectionMode

/**
 * The connection screen. It renders [ConnectionUiState] and emits [ConnectionAction]s; it has no
 * socket, protocol, or coroutine code.
 */
@Composable
internal fun ConnectionScreen(
    uiState: ConnectionUiState,
    onAction: (ConnectionAction) -> Unit,
    onManualNameChange: (String) -> Unit,
    onManualHostChange: (String) -> Unit,
    onManualPortChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CabScaffold(
        title = stringResource(R.string.connection_title),
        subtitle = stringResource(R.string.connection_subtitle),
        onBack = { onAction(ConnectionAction.Back) },
        modifier = modifier.testTag(ConnectionTestTags.SCREEN),
        actions = {
            StatusPill(status = uiState.topBarStatus(), attempt = uiState.link?.attempt)
        },
    ) { contentPadding ->
        Box(
            modifier =
            Modifier
                .padding(contentPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                Modifier
                    .widthIn(max = CabTheme.sizing.contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CabTheme.spacing.page, vertical = CabTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.md),
            ) {
                if (uiState.isDemoSession) {
                    DemoBanner()
                }

                uiState.link?.let { link ->
                    LinkCard(link = link, onAction = onAction)
                }

                uiState.failure?.let { failure ->
                    FailureCard(failure = failure, onAction = onAction)
                }

                if (uiState.permission != PermissionUiState.Granted &&
                    uiState.permission != PermissionUiState.Checking
                ) {
                    PermissionCard(permission = uiState.permission, onAction = onAction)
                }

                NearbySection(uiState = uiState, onAction = onAction)

                SavedSection(uiState = uiState, onAction = onAction)

                ManualEntrySection(onAction = onAction)

                DemoSection(uiState = uiState, onAction = onAction)
            }
        }
    }

    uiState.manualEntry?.let { manualEntry ->
        ManualEntrySheet(
            state = manualEntry,
            onNameChange = onManualNameChange,
            onHostChange = onManualHostChange,
            onPortChange = onManualPortChange,
            onSubmit = { onAction(ConnectionAction.SubmitManualEntry) },
            onDismiss = { onAction(ConnectionAction.CancelManualEntry) },
        )
    }
}

private fun ConnectionUiState.topBarStatus(): CabConnectionStatus = when (link?.phase) {
    LinkPhase.Connected -> CabConnectionStatus.Connected
    LinkPhase.Connecting, LinkPhase.Reconnecting -> CabConnectionStatus.Reconnecting
    null -> CabConnectionStatus.Offline
}

@Composable
private fun DemoBanner() {
    val extended = CabTheme.extendedColors
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .testTag(ConnectionTestTags.DEMO_BANNER),
        shape = CabTheme.shapes.medium,
        color = extended.cautionContainer,
        contentColor = extended.onCautionContainer,
    ) {
        Row(
            modifier = Modifier.padding(CabTheme.spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(CabTheme.sizing.iconMedium),
                tint = extended.caution,
            )
            Spacer(Modifier.width(CabTheme.spacing.sm))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.connection_demo_banner_title),
                    style = CabTheme.typography.sectionTitle,
                )
                Text(
                    text = stringResource(R.string.connection_demo_banner_body),
                    style = CabTheme.typography.readoutSmall,
                )
            }
        }
    }
}

@Composable
private fun LinkCard(
    link: LinkUiState,
    onAction: (ConnectionAction) -> Unit,
) {
    when (link.phase) {
        LinkPhase.Connected -> ConnectedCard(link = link, onAction = onAction)

        LinkPhase.Connecting -> ProgressCard(
            title = stringResource(R.string.connection_link_connecting, link.target.name),
            subtitle = link.target.endpoint.display,
            onCancel = { onAction(ConnectionAction.CancelConnection) },
            testTag = ConnectionTestTags.CONNECTING,
        )

        LinkPhase.Reconnecting -> ProgressCard(
            title = stringResource(
                R.string.connection_link_reconnecting,
                link.target.name,
                link.attempt,
            ),
            subtitle = link.target.endpoint.display,
            onCancel = { onAction(ConnectionAction.CancelConnection) },
            testTag = ConnectionTestTags.RECONNECTING,
        )
    }
}

@Composable
private fun ConnectedCard(
    link: LinkUiState,
    onAction: (ConnectionAction) -> Unit,
) {
    val demo = link.target.mode == ConnectionMode.DEMO
    SectionCard(
        title = if (demo) {
            stringResource(R.string.connection_link_connected_demo)
        } else {
            stringResource(R.string.connection_link_connected, link.target.name)
        },
        subtitle = if (demo) {
            stringResource(R.string.connection_link_demo_endpoint)
        } else {
            link.target.endpoint.display
        },
        modifier = Modifier.testTag(ConnectionTestTags.CONNECTED),
    ) {
        StatusPill(status = CabConnectionStatus.Connected)
        Text(
            text = stringResource(R.string.connection_link_ready),
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryActionButton(
            text = stringResource(R.string.connection_link_open_drive),
            onClick = { onAction(ConnectionAction.OpenDrive) },
            size = PrimaryActionSize.Large,
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ConnectionTestTags.OPEN_DRIVE),
        )
        if (demo) {
            TextButton(
                onClick = { onAction(ConnectionAction.EndDemo) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.connection_demo_end))
            }
        }
    }
}

@Composable
private fun ProgressCard(
    title: String,
    subtitle: String,
    onCancel: () -> Unit,
    testTag: String,
) {
    SectionCard(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.testTag(testTag),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = CabTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.connection_server_connecting),
                style = CabTheme.typography.readout,
                color = CabTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = CabTheme.spacing.sm),
            )
        }
        TextButton(
            onClick = onCancel,
            modifier =
            Modifier
                .align(Alignment.End)
                .testTag(ConnectionTestTags.CANCEL),
        ) {
            Text(stringResource(R.string.connection_cancel))
        }
    }
}

@Composable
private fun FailureCard(
    failure: FailureUiState,
    onAction: (ConnectionAction) -> Unit,
) {
    val copy = connectionErrorCopy(
        error = failure.error,
        hostPort = failure.target.endpoint.display,
    )
    val retryLabel = when (copy.action) {
        ConnectionErrorAction.Retry -> stringResource(R.string.connection_error_retry)
        ConnectionErrorAction.OpenSettings -> stringResource(R.string.connection_permission_settings)
        ConnectionErrorAction.Dismiss -> stringResource(R.string.connection_error_dismiss)
    }
    val retry = {
        when (copy.action) {
            ConnectionErrorAction.Retry -> onAction(ConnectionAction.Retry)
            ConnectionErrorAction.OpenSettings -> onAction(ConnectionAction.OpenSettings)
            ConnectionErrorAction.Dismiss -> onAction(ConnectionAction.DismissFailure)
        }
    }
    InlineError(
        title = copy.title,
        message = copy.message,
        onRetry = retry,
        retryLabel = retryLabel,
        modifier = Modifier.testTag(ConnectionTestTags.FAILED),
    )
}

@Composable
private fun PermissionCard(
    permission: PermissionUiState,
    onAction: (ConnectionAction) -> Unit,
) {
    val denied = permission == PermissionUiState.Denied
    SectionCard(
        title = stringResource(
            if (denied) {
                R.string.connection_permission_denied_title
            } else {
                R.string.connection_permission_title
            },
        ),
        modifier = Modifier.testTag(
            if (denied) {
                ConnectionTestTags.PERMISSION_DENIED
            } else {
                ConnectionTestTags.PERMISSION
            },
        ),
    ) {
        Text(
            text = stringResource(
                if (denied) {
                    R.string.connection_permission_denied_body
                } else {
                    R.string.connection_permission_body
                },
            ),
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
        if (denied) {
            PrimaryActionButton(
                text = stringResource(R.string.connection_permission_settings),
                onClick = { onAction(ConnectionAction.OpenSettings) },
                size = PrimaryActionSize.Regular,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(ConnectionTestTags.PERMISSION_SETTINGS),
            )
            TextButton(
                onClick = { onAction(ConnectionAction.RequestPermission) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.connection_permission_try_again))
            }
        } else {
            PrimaryActionButton(
                text = stringResource(R.string.connection_permission_allow),
                onClick = { onAction(ConnectionAction.RequestPermission) },
                size = PrimaryActionSize.Regular,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(ConnectionTestTags.PERMISSION_ALLOW),
            )
            TextButton(
                onClick = { onAction(ConnectionAction.OpenSettings) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.connection_permission_settings))
            }
        }
    }
}

@Composable
private fun NearbySection(
    uiState: ConnectionUiState,
    onAction: (ConnectionAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm)) {
        SectionHeading(
            title = stringResource(R.string.connection_nearby_title),
            action = {
                TextButton(
                    onClick = { onAction(ConnectionAction.Rescan) },
                    enabled = uiState.permission == PermissionUiState.Granted &&
                        uiState.scanPhase != ScanPhase.Scanning,
                ) {
                    Text(stringResource(R.string.connection_rescan))
                }
            },
        )

        when (uiState.permission) {
            PermissionUiState.Checking ->
                ScanningRow(
                    title = stringResource(R.string.connection_nearby_scanning_title),
                    detail = stringResource(R.string.connection_nearby_scanning_body),
                )

            PermissionUiState.Granted -> when {
                uiState.discovered.isNotEmpty() -> {
                    ServerList(
                        servers = uiState.discovered,
                        onAction = onAction,
                        testTag = "connection_discovered_list",
                    )
                    if (uiState.scanPhase == ScanPhase.Scanning) {
                        ScanningRow(text = stringResource(R.string.connection_nearby_still_scanning))
                    }
                }

                uiState.scanPhase == ScanPhase.Complete && uiState.discoveryError != null ->
                    DiscoveryError(
                        error = uiState.discoveryError,
                        onAction = onAction,
                    )

                uiState.scanPhase == ScanPhase.Complete ->
                    EmptyNearby()

                else -> ScanningRow(
                    title = stringResource(R.string.connection_nearby_scanning_title),
                    detail = stringResource(R.string.connection_nearby_scanning_body),
                )
            }

            PermissionUiState.Needed, PermissionUiState.Denied ->
                Text(
                    text = stringResource(R.string.connection_discovery_permission_body),
                    style = CabTheme.typography.readout,
                    color = CabTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
private fun ServerList(
    servers: List<ServerCardUiState>,
    onAction: (ConnectionAction) -> Unit,
    testTag: String,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs),
    ) {
        servers.forEach { server ->
            ServerCard(
                server = server,
                onConnect = { onAction(ConnectionAction.Connect(server)) },
                onCancel = { onAction(ConnectionAction.CancelConnection) },
                onRetry = { onAction(ConnectionAction.Retry) },
                onOpenDrive = { onAction(ConnectionAction.OpenDrive) },
                onEdit = if (server.source == ServerSource.Saved) {
                    { onAction(ConnectionAction.EditSaved(server)) }
                } else {
                    null
                },
                onDelete = if (server.source == ServerSource.Saved) {
                    { onAction(ConnectionAction.DeleteSaved(server)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun ScanningRow(
    text: String? = null,
    title: String? = null,
    detail: String? = null,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = CabTheme.spacing.sm)
            .testTag(ConnectionTestTags.SCANNING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = CabTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.padding(start = CabTheme.spacing.sm)) {
            val headline = title ?: text
            if (headline != null) {
                Text(
                    text = headline,
                    style = CabTheme.typography.readout,
                    color = CabTheme.colorScheme.onSurface,
                )
            }
            if (detail != null) {
                Text(
                    text = detail,
                    style = CabTheme.typography.readoutSmall,
                    color = CabTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyNearby() {
    EmptyState(
        title = stringResource(R.string.connection_empty_title),
        message = stringResource(R.string.connection_empty_body),
    )
}

@Composable
private fun DiscoveryError(
    error: ConnectionError,
    onAction: (ConnectionAction) -> Unit,
) {
    val message = if (error == ConnectionError.LocalNetworkPermissionDenied) {
        stringResource(R.string.connection_discovery_permission_body)
    } else {
        stringResource(R.string.connection_discovery_failed_body)
    }
    InlineError(
        title = stringResource(R.string.connection_discovery_failed_title),
        message = message,
        onRetry = { onAction(ConnectionAction.Rescan) },
        retryLabel = stringResource(R.string.connection_rescan),
    )
}

@Composable
private fun SavedSection(
    uiState: ConnectionUiState,
    onAction: (ConnectionAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm)) {
        SectionHeading(title = stringResource(R.string.connection_saved_title))
        if (uiState.saved.isEmpty()) {
            Text(
                text = stringResource(R.string.connection_saved_empty),
                style = CabTheme.typography.readout,
                color = CabTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ServerList(
                servers = uiState.saved,
                onAction = onAction,
                testTag = "connection_saved_list",
            )
        }
    }
}

@Composable
private fun ManualEntrySection(onAction: (ConnectionAction) -> Unit) {
    SectionCard(
        title = stringResource(R.string.connection_manual_section_title),
        subtitle = stringResource(R.string.connection_manual_section_body),
    ) {
        PrimaryActionButton(
            text = stringResource(R.string.connection_manual_open),
            onClick = { onAction(ConnectionAction.OpenManualEntry) },
            icon = Icons.Filled.Add,
            size = PrimaryActionSize.Regular,
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ConnectionTestTags.MANUAL_ENTRY),
        )
    }
}

@Composable
private fun DemoSection(
    uiState: ConnectionUiState,
    onAction: (ConnectionAction) -> Unit,
) {
    val demoLink = uiState.link?.takeIf { it.target.mode == ConnectionMode.DEMO }
    SectionCard(
        title = stringResource(R.string.connection_demo_title),
        subtitle = stringResource(R.string.connection_demo_subtitle),
    ) {
        Text(
            text = stringResource(R.string.connection_demo_body),
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
        when {
            demoLink?.phase == LinkPhase.Connected -> {
                StatusPill(status = CabConnectionStatus.Connected)
                Text(
                    text = stringResource(R.string.connection_demo_running),
                    style = CabTheme.typography.readout,
                    color = CabTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryActionButton(
                    text = stringResource(R.string.connection_link_open_drive),
                    onClick = { onAction(ConnectionAction.OpenDrive) },
                    size = PrimaryActionSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { onAction(ConnectionAction.EndDemo) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.connection_demo_end))
                }
            }

            demoLink != null -> {
                ScanningRow(text = stringResource(R.string.connection_demo_starting))
                TextButton(
                    onClick = { onAction(ConnectionAction.CancelConnection) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.connection_cancel))
                }
            }

            else ->
                PrimaryActionButton(
                    text = stringResource(R.string.connection_demo_start),
                    onClick = { onAction(ConnectionAction.StartDemo) },
                    size = PrimaryActionSize.Large,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(ConnectionTestTags.START_DEMO),
                )
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = CabTheme.colorScheme.onSurface,
            modifier =
            Modifier
                .weight(1f)
                .semantics { heading() },
        )
        action?.invoke()
    }
}
