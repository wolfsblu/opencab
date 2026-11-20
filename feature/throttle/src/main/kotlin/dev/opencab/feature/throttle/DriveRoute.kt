package dev.opencab.feature.throttle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.component.OpenCabScaffold
import dev.opencab.core.designsystem.theme.OpenCabTheme
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
    OpenCabScaffold(
        title = stringResource(R.string.drive_title),
        modifier = modifier.testTag("screen_drive"),
        actions = {
            IconButton(onClick = onOpenConnection) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.drive_connection_action),
                )
            }
        },
    ) { contentPadding ->
        val contentModifier =
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)

        Column(
            modifier = contentModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "0",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.drive_speed_unit),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            ConnectionCard(
                connectionState = connectionState,
                onOpenConnection = onOpenConnection,
            )
            Spacer(Modifier.height(16.dp))
            ThrottleCard()
        }
    }
}

@Composable
private fun ConnectionCard(
    connectionState: ConnectionState,
    onOpenConnection: () -> Unit,
) {
    val status =
        when (connectionState) {
            ConnectionState.Disconnected -> ConnectionStatus(
                label = stringResource(R.string.drive_status_disconnected),
                color = MaterialTheme.colorScheme.outline,
            )

            ConnectionState.Connecting -> ConnectionStatus(
                label = stringResource(R.string.drive_status_connecting),
                color = MaterialTheme.colorScheme.tertiary,
            )

            is ConnectionState.Connected -> ConnectionStatus(
                label = stringResource(R.string.drive_status_connected, connectionState.endpoint),
                color = MaterialTheme.colorScheme.primary,
            )
        }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(status.color),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.drive_status_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onOpenConnection,
                modifier = Modifier.testTag("drive_open_connection"),
            ) {
                Text(stringResource(R.string.drive_connection_action))
            }
        }
    }
}

@Composable
private fun ThrottleCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.drive_throttle_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.drive_throttle_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class ConnectionStatus(
    val label: String,
    val color: Color,
)

@Preview(showBackground = true, backgroundColor = 0xFFFAFAFF)
@Composable
internal fun DriveScreenPreview() {
    OpenCabTheme(dynamicColor = false) {
        DriveScreen(
            connectionState = ConnectionState.Disconnected,
            onOpenConnection = {},
        )
    }
}
