package dev.opencab.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tonal elevation levels.
 *
 * OpenCab does not use shadows for decoration. A raised surface is communicated by the M3
 * container role, not by a drop shadow; the shadow tokens exist only for components that
 * genuinely float above the content (sheets, dialogs).
 */
enum class CabElevationLevel {
    Flat,
    Raised,
    Overlay,
    Modal,
}

@Immutable
data class CabElevation(
    val raised: Dp = 1.dp,
    val overlay: Dp = 3.dp,
    val modal: Dp = 6.dp,
) {
    /**
     * The colour a surface at [level] should use. The caller is responsible for placing the
     * content on top with the matching `on*` role.
     */
    fun surfaceFor(
        level: CabElevationLevel,
        colorScheme: ColorScheme,
    ): Color = when (level) {
        CabElevationLevel.Flat -> colorScheme.surface
        CabElevationLevel.Raised -> colorScheme.surfaceContainerLow
        CabElevationLevel.Overlay -> colorScheme.surfaceContainerHigh
        CabElevationLevel.Modal -> colorScheme.surfaceContainerHighest
    }

    fun shadowFor(level: CabElevationLevel): Dp = when (level) {
        CabElevationLevel.Flat -> 0.dp
        CabElevationLevel.Raised -> raised
        CabElevationLevel.Overlay -> overlay
        CabElevationLevel.Modal -> modal
    }

    companion object {
        val Default: CabElevation = CabElevation()
    }
}

internal val LocalCabElevation = staticCompositionLocalOf { CabElevation.Default }
