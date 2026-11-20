package dev.opencab.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.opencab.core.designsystem.component.CabConnectionStatus
import dev.opencab.core.designsystem.component.CabScaffold
import dev.opencab.core.designsystem.component.InlineError
import dev.opencab.core.designsystem.component.PrimaryActionButton
import dev.opencab.core.designsystem.component.PrimaryActionSize
import dev.opencab.core.designsystem.component.SectionCard
import dev.opencab.core.designsystem.component.StatusPill
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.theme.CabTheme
import dev.opencab.core.model.ConnectionState

const val CONNECTION_ROUTE: String = "connection"

@Composable
fun ConnectionRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    connectionState: ConnectionState = ConnectionState.Disconnected,
) {
    val status = connectionState.toCabStatus()

    CabScaffold(
        title = stringResource(R.string.connection_title),
        subtitle = stringResource(R.string.connection_subtitle),
        modifier = modifier.testTag("screen_connection"),
        onBack = onBack,
        actions = {
            StatusPill(status = status)
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
                SectionCard(title = stringResource(R.string.connection_status_title)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
                    ) {
                        StatusPill(status = status)
                        Text(
                            text = stringResource(R.string.connection_status_body),
                            style = CabTheme.typography.readout,
                            color = CabTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SectionCard(title = stringResource(R.string.connection_protocol_title)) {
                    Text(
                        text = stringResource(R.string.connection_protocol_status),
                        style = CabTheme.typography.address,
                        color = CabTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.connection_protocol_body),
                        style = CabTheme.typography.readout,
                        color = CabTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InlineError(
                    title = stringResource(R.string.connection_unavailable_title),
                    message = stringResource(R.string.connection_unavailable_body),
                )
                PrimaryActionButton(
                    text = stringResource(R.string.connection_connect_unavailable),
                    onClick = {},
                    enabled = false,
                    size = PrimaryActionSize.Regular,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
private fun ConnectionScreenPreview() {
    CabTheme {
        ConnectionRoute(
            onBack = {},
            connectionState = ConnectionState.Disconnected,
        )
    }
}
