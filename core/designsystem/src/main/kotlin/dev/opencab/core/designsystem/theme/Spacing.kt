package dev.opencab.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A 4 dp spacing grid. Screens should compose from these values rather than inventing
 * one-off paddings, so density stays consistent across features.
 */
@Immutable
data class CabSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val page: Dp = 20.dp,
    val gutter: Dp = 16.dp,
    val cardPadding: Dp = 20.dp,
) {
    companion object {
        val Default: CabSpacing = CabSpacing()
    }
}

/**
 * Touch and component sizing tokens. The minimum is the accessibility floor; the larger
 * values are the deliberate targets from the UX specification.
 */
@Immutable
data class CabSizing(
    val touchMin: Dp = 48.dp,
    val touchSecondary: Dp = 64.dp,
    val touchPrimary: Dp = 72.dp,
    val functionKey: Dp = 56.dp,
    val speedArcCompact: Dp = 232.dp,
    val speedArcMax: Dp = 320.dp,
    val speedStroke: Dp = 10.dp,
    val controlDeck: Dp = 72.dp,
    val leverTrackWidth: Dp = 16.dp,
    val leverHandleWidth: Dp = 112.dp,
    val leverHandleHeight: Dp = 60.dp,
    val contentMaxWidth: Dp = 640.dp,
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 24.dp,
    val iconLarge: Dp = 40.dp,
    val statusGlyph: Dp = 12.dp,
) {
    companion object {
        val Default: CabSizing = CabSizing()
    }
}

internal val LocalCabSpacing = staticCompositionLocalOf { CabSpacing.Default }
internal val LocalCabSizing = staticCompositionLocalOf { CabSizing.Default }
