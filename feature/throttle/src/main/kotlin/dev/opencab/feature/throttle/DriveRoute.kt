package dev.opencab.feature.throttle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import dev.opencab.core.designsystem.component.SectionCard
import dev.opencab.core.designsystem.component.SpeedDisplay
import dev.opencab.core.designsystem.component.StatusPill
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.core.model.ConnectionMode
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
            is ConnectionState.Connected ->
                if (connectionState.target.mode == ConnectionMode.DEMO) {
                    stringResource(R.string.drive_subtitle_demo)
                } else {
                    connectionState.target.endpoint.display
                }

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
            if (connectionState is ConnectionState.Connected &&
                connectionState.target.mode == ConnectionMode.DEMO
            ) {
                DriveDemoBanner()
            }
            SpeedDisplay(
                percent = 0,
                direction = CabDirection.Forward,
                enabled = connectionState is ConnectionState.Connected,
            )
            LocomotiveCard(connected = connectionState is ConnectionState.Connected)
            LinkCard(
                connectionState = connectionState,
                onOpenConnection = onOpenConnection,
                onDisconnect = onDisconnect,
            )
            PrimaryActionButton(
                text = stringResource(R.string.drive_stop),
                onClick = {},
                enabled = connectionState is ConnectionState.Connected,
                size = PrimaryActionSize.Hero,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DriveDemoBanner() {
    val extended = CabTheme.extendedColors
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .testTag("drive_demo_banner"),
        shape = RoundedCornerShape(12.dp),
        color = extended.cautionContainer,
        contentColor = extended.onCautionContainer,
    ) {
        Row(
            modifier = Modifier.padding(CabTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(CabTheme.sizing.iconMedium),
                tint = extended.caution,
            )
            Spacer(Modifier.width(CabTheme.spacing.sm))
            Column {
                Text(
                    text = stringResource(R.string.drive_demo_banner_title),
                    style = CabTheme.typography.sectionTitle,
                )
                Text(
                    text = stringResource(R.string.drive_demo_banner_body),
                    style = CabTheme.typography.readoutSmall,
                )
            }
        }
    }
}

@Composable
private fun LocomotiveCard(connected: Boolean) {
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
            text = stringResource(
                if (connected) {
                    R.string.drive_locomotive_body_connected
                } else {
                    R.string.drive_locomotive_body
                },
            ),
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinkCard(
    connectionState: ConnectionState,
    onOpenConnection: () -> Unit,
    onDisconnect: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.drive_link_title)) {
        Text(
            text = when (connectionState) {
                is ConnectionState.Connected ->
                    if (connectionState.target.mode == ConnectionMode.DEMO) {
                        stringResource(R.string.drive_link_connected_demo)
                    } else {
                        stringResource(
                            R.string.drive_link_connected,
                            connectionState.target.name,
                            connectionState.target.endpoint.display,
                        )
                    }

                is ConnectionState.Connecting ->
                    stringResource(R.string.drive_link_connecting, connectionState.target.name)

                is ConnectionState.Reconnecting ->
                    stringResource(
                        R.string.drive_link_reconnecting,
                        connectionState.target.name,
                        connectionState.attempt,
                    )

                is ConnectionState.Failed -> stringResource(R.string.drive_link_failed)

                ConnectionState.Disconnected -> stringResource(R.string.drive_link_disconnected)
            },
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm)) {
            TextButton(
                onClick = onOpenConnection,
                modifier = Modifier.testTag("drive_open_connection"),
            ) {
                Text(stringResource(R.string.drive_connection_action))
            }
            if (connectionState is ConnectionState.Connected) {
                TextButton(
                    onClick = onDisconnect,
                    modifier = Modifier.testTag("drive_disconnect"),
                ) {
                    Text(stringResource(R.string.drive_disconnect))
                }
            }
        }
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
