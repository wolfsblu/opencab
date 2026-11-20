package dev.opencab.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The M3 corner scale, tuned for instrument panels: controls are softly rectangular, sheets
 * are generous, and only the status pill is fully round.
 */
internal val CabMaterialShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

@Immutable
data class CabShapeScale(
    val extraSmall: Shape,
    val small: Shape,
    val medium: Shape,
    val large: Shape,
    val extraLarge: Shape,
    val pill: Shape,
    val control: Shape,
    val panel: Shape,
    val sheet: Shape,
    val indicator: Shape,
) {
    companion object {
        val Default: CabShapeScale =
            CabShapeScale(
                extraSmall = RoundedCornerShape(4.dp),
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(28.dp),
                pill = CircleShape,
                control = RoundedCornerShape(14.dp),
                panel = RoundedCornerShape(18.dp),
                sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                indicator = RoundedCornerShape(3.dp),
            )
    }
}

internal val LocalCabShapeScale = staticCompositionLocalOf { CabShapeScale.Default }
