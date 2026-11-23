package dev.opencab.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * A calm, centred empty state. The icon is decorative; the title carries the message.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .widthIn(max = CabTheme.sizing.contentMaxWidth)
            .padding(horizontal = CabTheme.spacing.md, vertical = CabTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = CabTheme.colorScheme.primaryContainer,
            contentColor = CabTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier =
                Modifier
                    .padding(CabTheme.spacing.lg)
                    .size(CabTheme.sizing.iconLarge),
                tint = CabTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(CabTheme.spacing.md))
        Text(
            text = title,
            style = CabTheme.typography.sectionTitle,
            color = CabTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(CabTheme.spacing.xs))
        Text(
            text = message,
            style = CabTheme.typography.readout,
            color = CabTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(CabTheme.spacing.md))
            action()
        }
    }
}

@CabPreview
@Composable
private fun EmptyStatePreview() {
    CabPreviewSurface {
        EmptyState(
            title = "No locomotives yet",
            message = "Add a locomotive to the roster to start driving.",
        )
    }
}
