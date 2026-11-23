package dev.opencab.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.R
import dev.opencab.core.designsystem.preview.CabPreview
import dev.opencab.core.designsystem.preview.CabPreviewSurface
import dev.opencab.core.designsystem.theme.CabTheme

enum class PrimaryActionSize(
    val minHeight: Dp,
) {
    Regular(56.dp),
    Large(64.dp),
}

/** The visual weight of a primary action. Destructive is reserved for stopping or releasing. */
enum class PrimaryActionTone {
    Default,
    Destructive,
}

/**
 * The one primary action on a screen.
 *
 * [Regular] is the default so every ordinary screen action shares the same 56 dp height, and
 * Stop uses it too. [Large] is reserved for a single emphasised action.
 */
@Composable
@Suppress("LongParameterList")
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    size: PrimaryActionSize = PrimaryActionSize.Regular,
    tone: PrimaryActionTone = PrimaryActionTone.Default,
) {
    val loadingLabel = stringResource(R.string.cab_loading)
    val colors =
        when (tone) {
            PrimaryActionTone.Default -> ButtonDefaults.buttonColors()

            PrimaryActionTone.Destructive ->
                ButtonDefaults.buttonColors(
                    containerColor = CabTheme.extendedColors.stop,
                    contentColor = CabTheme.extendedColors.onStop,
                )
        }
    Button(
        onClick = onClick,
        modifier =
        modifier
            .heightIn(min = size.minHeight)
            .semantics {
                if (loading) stateDescription = loadingLabel
            },
        enabled = enabled && !loading,
        shape = CabTheme.shapes.pill,
        colors = colors,
        contentPadding =
        PaddingValues(
            horizontal = CabTheme.spacing.lg,
            vertical = CabTheme.spacing.sm,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(CabTheme.spacing.sm))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(CabTheme.spacing.sm))
            }
            Text(
                text = text,
                style = CabTheme.typography.sectionTitle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@CabPreview
@Composable
private fun PrimaryActionButtonPreview() {
    CabPreviewSurface {
        PrimaryActionButton(
            text = "Stop train",
            onClick = {},
        )
    }
}
