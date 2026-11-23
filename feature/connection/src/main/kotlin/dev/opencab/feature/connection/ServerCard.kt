package dev.opencab.feature.connection

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.component.CabConnectionStatus
import dev.opencab.core.designsystem.component.SecondaryActionButton
import dev.opencab.core.designsystem.component.StatusPill
import dev.opencab.core.designsystem.theme.CabElevationLevel
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * One discovered or saved layout.
 *
 * Status always pairs an icon or spinner with a word. The card is tappable only while it is
 * available; in every other state the explicit secondary action owns the gesture.
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
internal fun ServerCard(
    server: ServerCardUiState,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenDrive: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = CabTheme.shapes.panel
    val canConnect = server.status == ServerStatus.Available
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier =
        modifier
            .fillMaxWidth()
            .testTag("server_card_${server.id}")
            .clip(shape)
            .clickable(
                enabled = canConnect,
                role = Role.Button,
                onClick = onConnect,
            ),
        shape = shape,
        color = CabTheme.elevation.surfaceFor(CabElevationLevel.Raised, CabTheme.colorScheme),
        contentColor = CabTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, CabTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(CabTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = CabTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = server.hostPort,
                        style = CabTheme.typography.readout,
                        color = CabTheme.colorScheme.onSurfaceVariant,
                    )
                    server.lastConnectedEpochMillis?.let { lastConnected ->
                        Text(
                            text = stringResource(
                                R.string.connection_server_last_connected,
                                relativeTime(lastConnected),
                            ),
                            style = CabTheme.typography.readoutSmall,
                            color = CabTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ServerStatusLabel(status = server.status)
                if (onEdit != null && onDelete != null) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(CabTheme.sizing.touchMin),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.connection_server_more),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.connection_server_edit)) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.connection_server_forget)) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }

            when (server.status) {
                ServerStatus.Available ->
                    SecondaryActionButton(
                        text = stringResource(R.string.connection_server_connect),
                        onClick = onConnect,
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("server_connect_${server.id}"),
                    )

                ServerStatus.Connecting ->
                    SecondaryActionButton(
                        text = stringResource(R.string.connection_cancel),
                        onClick = onCancel,
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("server_cancel_${server.id}"),
                    )

                ServerStatus.Connected ->
                    SecondaryActionButton(
                        text = stringResource(R.string.connection_server_drive),
                        onClick = onOpenDrive,
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("server_drive_${server.id}"),
                    )

                ServerStatus.Failed ->
                    SecondaryActionButton(
                        text = stringResource(R.string.connection_retry),
                        onClick = onRetry,
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("server_retry_${server.id}"),
                    )
            }
        }
    }
}

@Composable
private fun ServerStatusLabel(status: ServerStatus) {
    when (status) {
        ServerStatus.Available -> Unit

        ServerStatus.Connecting ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = CabTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.connection_server_connecting),
                    style = CabTheme.typography.status,
                    color = CabTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = CabTheme.spacing.xs),
                )
            }

        ServerStatus.Connected -> StatusPill(status = CabConnectionStatus.Connected)

        ServerStatus.Failed ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = CabTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.connection_server_failed),
                    style = CabTheme.typography.status,
                    color = CabTheme.colorScheme.error,
                    modifier = Modifier.padding(start = CabTheme.spacing.xs),
                )
            }
    }
}

@Composable
private fun relativeTime(epochMillis: Long): String {
    val elapsed = System.currentTimeMillis() - epochMillis
    if (elapsed in 0 until DateUtils.MINUTE_IN_MILLIS) {
        return stringResource(R.string.connection_server_last_connected_now)
    }
    return DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}
