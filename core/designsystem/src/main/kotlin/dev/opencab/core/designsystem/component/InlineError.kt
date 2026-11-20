package dev.opencab.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.R
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * A non-blocking error with an optional recovery action. The error text is a polite live
 * region so it is announced when it appears without interrupting an in-progress action.
 */
@Composable
fun InlineError(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = stringResource(R.string.cab_retry),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CabTheme.shapes.medium,
        color = CabTheme.colorScheme.errorContainer,
        contentColor = CabTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(CabTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.xs),
        ) {
            Row(
                modifier =
                Modifier.semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(CabTheme.sizing.iconMedium),
                    tint = CabTheme.colorScheme.error,
                )
                Spacer(Modifier.width(CabTheme.spacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = CabTheme.typography.sectionTitle,
                        color = CabTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = message,
                        style = CabTheme.typography.readout,
                        color = CabTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            if (onRetry != null) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = retryLabel, color = CabTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@CabPreview
@Composable
private fun InlineErrorPreview() {
    CabPreviewSurface {
        InlineError(
            title = "Nothing answered at that address",
            message = "Check the host and port, then try again.",
            onRetry = {},
        )
    }
}
