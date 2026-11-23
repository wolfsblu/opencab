package dev.opencab.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * The one secondary action in a group.
 *
 * Raw [TextButton]s default to a 40 dp height, which is shorter than the 48 dp accessibility
 * floor and visibly mismatched beside a primary action. Every secondary action in OpenCab goes
 * through this component so a row of controls always shares one height.
 */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = Color.Unspecified,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = CabTheme.sizing.touchMin),
        enabled = enabled,
        colors =
        if (contentColor == Color.Unspecified) {
            ButtonDefaults.textButtonColors()
        } else {
            ButtonDefaults.textButtonColors(contentColor = contentColor)
        },
        contentPadding =
        PaddingValues(
            horizontal = CabTheme.spacing.md,
            vertical = CabTheme.spacing.sm,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(CabTheme.spacing.xs))
            }
            Text(
                text = text,
                style = CabTheme.typography.sectionTitle,
            )
        }
    }
}

@CabPreview
@Composable
private fun SecondaryActionButtonPreview() {
    CabPreviewSurface {
        SecondaryActionButton(
            text = "Open connection",
            onClick = {},
        )
    }
}
