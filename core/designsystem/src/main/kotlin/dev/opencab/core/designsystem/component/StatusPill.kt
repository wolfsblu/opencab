package dev.opencab.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.R
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabTheme

enum class CabConnectionStatus {
    Connected,
    Reconnecting,
    Offline,
}

/**
 * The single accessible statement of link state.
 *
 * The pill pairs an icon with a word, so it never relies on colour alone. TalkBack receives
 * one merged node: "Connected", "Reconnecting, attempt 3", or "Offline".
 */
@Composable
fun StatusPill(
    status: CabConnectionStatus,
    modifier: Modifier = Modifier,
    attempt: Int? = null,
) {
    val extended = CabTheme.extendedColors
    val statusColors =
        when (status) {
            CabConnectionStatus.Connected ->
                StatusColors(
                    container = extended.liveContainer,
                    content = extended.onLiveContainer,
                    glyph = extended.live,
                    icon = Icons.Filled.CheckCircle,
                )

            CabConnectionStatus.Reconnecting ->
                StatusColors(
                    container = extended.cautionContainer,
                    content = extended.onCautionContainer,
                    glyph = extended.caution,
                    icon = Icons.Filled.Refresh,
                )

            CabConnectionStatus.Offline ->
                StatusColors(
                    container = extended.offlineContainer,
                    content = extended.onOfflineContainer,
                    glyph = extended.offline,
                    icon = Icons.Filled.Warning,
                )
        }
    val container = statusColors.container
    val content = statusColors.content
    val glyph = statusColors.glyph
    val icon = statusColors.icon

    val description =
        when (status) {
            CabConnectionStatus.Connected -> stringResource(R.string.cab_status_connected)

            CabConnectionStatus.Reconnecting ->
                if (attempt != null && attempt > 0) {
                    stringResource(R.string.cab_status_reconnecting_attempt, attempt)
                } else {
                    stringResource(R.string.cab_status_reconnecting)
                }

            CabConnectionStatus.Offline -> stringResource(R.string.cab_status_offline)
        }

    Surface(
        modifier =
        modifier
            .heightIn(min = 32.dp)
            .clearAndSetSemantics { contentDescription = description },
        shape = CabTheme.shapes.pill,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(CabTheme.sizing.statusGlyph),
                tint = glyph,
            )
            Spacer(Modifier.width(CabTheme.spacing.xs))
            Text(
                text = description,
                style = CabTheme.typography.status,
                color = content,
            )
        }
    }
}

private data class StatusColors(
    val container: Color,
    val content: Color,
    val glyph: Color,
    val icon: ImageVector,
)

@CabPreview
@Composable
private fun StatusPillPreview() {
    CabPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(status = CabConnectionStatus.Connected)
            StatusPill(status = CabConnectionStatus.Reconnecting, attempt = 3)
            StatusPill(status = CabConnectionStatus.Offline)
        }
    }
}
