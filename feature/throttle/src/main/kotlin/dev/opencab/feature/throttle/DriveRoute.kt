package dev.opencab.feature.throttle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.component.CabConnectionStatus
import dev.opencab.core.designsystem.component.CabDirection
import dev.opencab.core.designsystem.component.CabScaffold
import dev.opencab.core.designsystem.component.PrimaryActionButton
import dev.opencab.core.designsystem.component.PrimaryActionSize
import dev.opencab.core.designsystem.component.SecondaryActionButton
import dev.opencab.core.designsystem.component.SectionCard
import dev.opencab.core.designsystem.component.SpeedDisplay
import dev.opencab.core.designsystem.component.StatusPill
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.core.model.ConnectionState

const val DRIVE_ROUTE: String = "drive"

@Composable
fun DriveRoute(
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
    connectionState: ConnectionState = ConnectionState.Disconnected,
    onDisconnect: () -> Unit = {},
) {
    DriveScreen(
        connectionState = connectionState,
        onOpenConnection = onOpenConnection,
        onDisconnect = onDisconnect,
        modifier = modifier,
    )
}

@Composable
internal fun DriveScreen(
    connectionState: ConnectionState,
    onOpenConnection: () -> Unit,
    onDisconnect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val status = connectionState.toCabStatus()
    val subtitle =
        when (connectionState) {
            is ConnectionState.Connected -> connectionState.target.name

            is ConnectionState.Connecting ->
                stringResource(R.string.drive_subtitle_connecting_to, connectionState.target.name)

            is ConnectionState.Reconnecting ->
                stringResource(
                    R.string.drive_subtitle_reconnecting,
                    connectionState.target.name,
                    connectionState.attempt,
                )

            is ConnectionState.Failed -> stringResource(R.string.drive_subtitle_no_link)

            ConnectionState.Disconnected -> stringResource(R.string.drive_subtitle_no_link)
        }

    CabScaffold(
        title = stringResource(R.string.drive_title),
        subtitle = subtitle,
        modifier = modifier.testTag("screen_drive"),
        actions = {
            StatusPill(
                status = status,
                attempt = (connectionState as? ConnectionState.Reconnecting)?.attempt,
            )
            DriveActionsMenu(
                connectionState = connectionState,
                onOpenConnection = onOpenConnection,
                onDisconnect = onDisconnect,
            )
        },
    ) { contentPadding ->
        DriveContent(
            connectionState = connectionState,
            onOpenConnection = onOpenConnection,
            onDisconnect = onDisconnect,
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun DriveContent(
    connectionState: ConnectionState,
    onOpenConnection: () -> Unit,
    onDisconnect: () -> Unit,
    contentPadding: PaddingValues,
) {
    val connected = connectionState is ConnectionState.Connected
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.md),
        ) {
            SpeedDisplay(
                percent = 0,
                direction = CabDirection.Forward,
                enabled = connected,
            )

            when (connectionState) {
                is ConnectionState.Connected -> {
                    LocomotiveCard()
                    PrimaryActionButton(
                        text = stringResource(R.string.drive_stop),
                        onClick = {},
                        enabled = true,
                        size = PrimaryActionSize.Hero,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is ConnectionState.Connecting ->
                    ConnectionProgressCard(
                        title = stringResource(R.string.drive_connecting_title),
                        body = stringResource(
                            R.string.drive_connecting_body,
                            connectionState.target.name,
                        ),
                        onCancel = onDisconnect,
                    )

                is ConnectionState.Reconnecting ->
                    ConnectionProgressCard(
                        title = stringResource(R.string.drive_reconnecting_title),
                        body = stringResource(
                            R.string.drive_reconnecting_body,
                            connectionState.target.name,
                            connectionState.attempt,
                        ),
                        onCancel = onDisconnect,
                    )

                is ConnectionState.Failed ->
                    ConnectPrompt(
                        title = stringResource(R.string.drive_failed_title),
                        body = stringResource(R.string.drive_failed_body),
                        onOpenConnection = onOpenConnection,
                    )

                ConnectionState.Disconnected ->
                    ConnectPrompt(
                        title = stringResource(R.string.drive_no_link_title),
                        body = stringResource(R.string.drive_no_link_body),
                        onOpenConnection = onOpenConnection,
                    )
            }
        }
    }
}

@Composable
private fun DriveActionsMenu(
    connectionState: ConnectionState,
    onOpenConnection: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val canDisconnect =
        connectionState is ConnectionState.Connected ||
            connectionState is ConnectionState.Connecting ||
            connectionState is ConnectionState.Reconnecting
    val disconnectLabel =
        if (connectionState is ConnectionState.Connected) {
            stringResource(R.string.drive_disconnect)
        } else {
            stringResource(R.string.drive_cancel_connection)
        }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("drive_more"),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.drive_more_actions),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drive_connection_action)) },
                onClick = {
                    expanded = false
                    onOpenConnection()
                },
                modifier = Modifier.testTag("drive_open_connection"),
            )
            if (canDisconnect) {
                DropdownMenuItem(
                    text = { Text(disconnectLabel) },
                    onClick = {
                        expanded = false
                        onDisconnect()
                    },
                    modifier = Modifier.testTag("drive_disconnect"),
                )
            }
        }
    }
}

@Composable
private fun ConnectionProgressCard(
    title: String,
    body: String,
    onCancel: () -> Unit,
) {
    SectionCard(title = title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = CabTheme.colorScheme.primary,
            )
            Text(
                text = body,
                style = CabTheme.typography.readout,
                color = CabTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = CabTheme.spacing.sm),
            )
        }
        SecondaryActionButton(
            text = stringResource(R.string.drive_cancel_connection),
            onClick = onCancel,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun ConnectPrompt(
    title: String,
    body: String,
    onOpenConnection: () -> Unit,
) {
    SectionCard(title = title) {
        Text(
            text = body,
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryActionButton(
            text = stringResource(R.string.drive_find_layout),
            onClick = onOpenConnection,
            icon = Icons.Filled.Search,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LocomotiveCard() {
    SectionCard(
        title = stringResource(R.string.drive_locomotive_title),
        subtitle = stringResource(R.string.drive_locomotive_subtitle),
    ) {
        Text(
            text = "----",
            style = CabTheme.typography.address,
            color = CabTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.drive_locomotive_body_connected),
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ConnectionState.toCabStatus(): CabConnectionStatus = when (this) {
    is ConnectionState.Connected -> CabConnectionStatus.Connected
    is ConnectionState.Connecting, is ConnectionState.Reconnecting -> CabConnectionStatus.Reconnecting
    is ConnectionState.Failed, ConnectionState.Disconnected -> CabConnectionStatus.Offline
}

@CabPreview
@Composable
private fun DriveScreenPreview() {
    CabTheme {
        DriveScreen(
            connectionState = ConnectionState.Disconnected,
            onOpenConnection = {},
        )
    }
}
