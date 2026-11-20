package dev.opencab.feature.throttle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.opencab.core.model.ConnectionState

const val DRIVE_ROUTE: String = "drive"

@Composable
fun DriveRoute(
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DriveScreen(
        connectionState = ConnectionState.Disconnected,
        onOpenConnection = onOpenConnection,
        modifier = modifier,
    )
}

@Composable
internal fun DriveScreen(
    connectionState: ConnectionState,
    onOpenConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = connectionState.toCabStatus()
    val subtitle =
        when (connectionState) {
            is ConnectionState.Connected -> connectionState.endpoint
            ConnectionState.Connecting -> stringResource(R.string.drive_subtitle_connecting)
            ConnectionState.Disconnected -> stringResource(R.string.drive_subtitle_no_link)
        }

    CabScaffold(
        title = stringResource(R.string.drive_title),
        subtitle = subtitle,
        modifier = modifier.testTag("screen_drive"),
        actions = {
            StatusPill(status = status)
        },
    ) { contentPadding ->
        DriveContent(
            connectionState = connectionState,
            onOpenConnection = onOpenConnection,
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun DriveContent(
    connectionState: ConnectionState,
    onOpenConnection: () -> Unit,
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
            SpeedDisplay(
                percent = 0,
                direction = CabDirection.Forward,
                enabled = connectionState is ConnectionState.Connected,
            )
            LocomotiveCard()
            LinkCard(onOpenConnection = onOpenConnection)
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
            text = stringResource(R.string.drive_locomotive_body),
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinkCard(onOpenConnection: () -> Unit) {
    SectionCard(title = stringResource(R.string.drive_link_title)) {
        Text(
            text = stringResource(R.string.drive_link_body),
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onOpenConnection,
            modifier = Modifier.testTag("drive_open_connection"),
        ) {
            Text(stringResource(R.string.drive_connection_action))
        }
    }
}

private fun ConnectionState.toCabStatus(): CabConnectionStatus = when (this) {
    is ConnectionState.Connected -> CabConnectionStatus.Connected
    ConnectionState.Connecting -> CabConnectionStatus.Reconnecting
    ConnectionState.Disconnected -> CabConnectionStatus.Offline
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
