package dev.opencab.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * WCAG 2.x contrast helpers for the branded and dynamic colour schemes.
 *
 * OpenCab only adopts a generated dynamic scheme when its essential text and icon pairs are
 * readable. Dynamic colour is a preference; legibility is a product requirement.
 */
internal object CabContrast {
    const val BODY_TEXT: Float = 4.5f
    const val LARGE_TEXT: Float = 3.0f

    fun ratio(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Returns true when every pair OpenCab relies on for body copy, controls, and status glyphs
     * clears its minimum ratio. Anything less falls back to the branded scheme.
     */
    fun isReadable(scheme: ColorScheme): Boolean = ratio(scheme.onBackground, scheme.background) >= BODY_TEXT &&
        ratio(scheme.onSurface, scheme.surface) >= BODY_TEXT &&
        ratio(scheme.onSurface, scheme.surfaceContainerHigh) >= BODY_TEXT &&
        ratio(scheme.onSurfaceVariant, scheme.surface) >= BODY_TEXT &&
        ratio(scheme.onPrimary, scheme.primary) >= BODY_TEXT &&
        ratio(scheme.onPrimaryContainer, scheme.primaryContainer) >= BODY_TEXT &&
        ratio(scheme.onError, scheme.error) >= BODY_TEXT &&
        ratio(scheme.onErrorContainer, scheme.errorContainer) >= BODY_TEXT &&
        ratio(scheme.primary, scheme.surface) >= LARGE_TEXT &&
        ratio(scheme.error, scheme.surface) >= LARGE_TEXT
}
