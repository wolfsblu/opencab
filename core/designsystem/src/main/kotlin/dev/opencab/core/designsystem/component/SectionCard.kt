package dev.opencab.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabElevationLevel
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * A grouped surface for related readouts and controls.
 *
 * The container is a tonal surface, not a shadowed card. A hairline border keeps the panel
 * legible on glare-heavy, low-contrast displays without raising it above the content.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CabTheme.shapes.panel,
        color = CabTheme.elevation.surfaceFor(CabElevationLevel.Raised, CabTheme.colorScheme),
        contentColor = CabTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, CabTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(CabTheme.spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(CabTheme.spacing.sm),
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = CabTheme.typography.sectionTitle,
                            color = CabTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = CabTheme.typography.readoutSmall,
                                color = CabTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

@CabPreview
@Composable
private fun SectionCardPreview() {
    CabPreviewSurface {
        SectionCard(
            title = "Locomotive",
            subtitle = "3412 · Black Five",
        ) {
            Text(
                text = "No address acquired.",
                style = CabTheme.typography.readout,
                color = CabTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
