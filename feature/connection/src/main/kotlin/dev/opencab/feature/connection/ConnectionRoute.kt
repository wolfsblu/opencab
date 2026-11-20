package dev.opencab.feature.connection

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.component.OpenCabScaffold
import dev.opencab.core.designsystem.theme.OpenCabTheme
import dev.opencab.core.model.ConnectionState

const val CONNECTION_ROUTE: String = "connection"

@Composable
fun ConnectionRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    connectionState: ConnectionState = ConnectionState.Disconnected,
) {
    OpenCabScaffold(
        title = stringResource(R.string.connection_title),
        modifier = modifier.testTag("screen_connection"),
        onBack = onBack,
    ) { contentPadding ->
        val contentModifier =
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)

        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(connectionState = connectionState)
            ProtocolCard()
        }
    }
}

@Composable
private fun StatusCard(connectionState: ConnectionState) {
    val label =
        when (connectionState) {
            ConnectionState.Disconnected -> stringResource(R.string.connection_disconnected)

            ConnectionState.Connecting -> stringResource(R.string.connection_connecting)

            is ConnectionState.Connected ->
                stringResource(R.string.connection_connected, connectionState.endpoint)
        }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.outline,
                ) {}
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.connection_status_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {},
                enabled = false,
            ) {
                Text(stringResource(R.string.connection_connect_unavailable))
            }
        }
    }
}

@Composable
private fun ProtocolCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.connection_protocol_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.connection_protocol_status),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.connection_protocol_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAFF)
@Composable
internal fun ConnectionScreenPreview() {
    OpenCabTheme(dynamicColor = false) {
        ConnectionRoute(
            onBack = {},
            connectionState = ConnectionState.Disconnected,
        )
    }
}
