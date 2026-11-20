package dev.opencab.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.R
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * Screen scaffold with a status-only top bar that grows with the user's font scale.
 *
 * Insets are handled once, here: the top bar consumes the status-bar inset, and the
 * surrounding adaptive navigation suite consumes the bottom/start navigation inset. Screens
 * should apply the resulting [PaddingValues] rather than adding their own system-bar padding.
 */
@Composable
fun CabScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CabTopBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                actions = actions,
            )
        },
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

@Composable
private fun CabTopBar(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = CabTheme.colorScheme.surface,
        contentColor = CabTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(CabTheme.sizing.touchMin),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cab_back),
                        )
                    }
                } else {
                    Spacer(Modifier.width(CabTheme.spacing.md))
                }
                Column(
                    modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = CabTheme.spacing.xs),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = CabTheme.colorScheme.onSurface,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = CabTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                actions()
            }
            HorizontalDivider(color = CabTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }
    }
}

@CabPreview
@Composable
private fun CabScaffoldPreview() {
    CabPreviewSurface {
        CabScaffold(
            title = "Drive",
            subtitle = "Connected to Junction Yard",
            onBack = {},
            actions = {
                StatusPill(status = CabConnectionStatus.Connected)
            },
        ) { padding ->
            Text(
                text = "Screen content",
                modifier = Modifier.padding(padding),
                style = CabTheme.typography.readout,
            )
        }
    }
}
