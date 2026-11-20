package dev.opencab.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

internal val LocalCabExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * The OpenCab theme.
 *
 * Dynamic colour is opt-in. The branded fallback is the default because status colours,
 * contrast, and the instrument-panel identity have to survive on every device. When dynamic
 * colour is requested and the generated scheme fails OpenCab's essential contrast pairs, the
 * branded scheme is used instead.
 *
 * [highContrast] selects a hand-tuned high-contrast fallback. It is intentionally incompatible
 * with dynamic colour: a generated palette cannot guarantee the exhibition-glare contrast
 * target.
 */
@Composable
fun CabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    highContrast: Boolean = false,
    reducedMotion: Boolean = rememberReducedMotion(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val fallback =
        when {
            highContrast && darkTheme -> DarkHighContrastColorScheme
            highContrast -> LightHighContrastColorScheme
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    val extendedColors =
        when {
            highContrast && darkTheme -> DarkHighContrastExtendedColors
            highContrast -> LightHighContrastExtendedColors
            darkTheme -> DarkExtendedColors
            else -> LightExtendedColors
        }

    val colorScheme: ColorScheme =
        if (dynamicColor && !highContrast && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val generated =
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            if (CabContrast.isReadable(generated)) generated else fallback
        } else {
            fallback
        }

    val motion = remember(reducedMotion) { CabMotion(reduced = reducedMotion) }
    val haptics = rememberCabHaptics()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CabMaterialTypography,
        shapes = CabMaterialShapes,
    ) {
        CompositionLocalProvider(
            LocalCabExtendedColors provides extendedColors,
            LocalCabTypeScale provides CabTypeScale.Default,
            LocalCabShapeScale provides CabShapeScale.Default,
            LocalCabSpacing provides CabSpacing.Default,
            LocalCabSizing provides CabSizing.Default,
            LocalCabElevation provides CabElevation.Default,
            LocalCabMotion provides motion,
            LocalCabHaptics provides haptics,
            content = content,
        )
    }
}

/**
 * Read-only accessors for OpenCab's design tokens.
 *
 * Material roles stay available through `MaterialTheme`; this object adds the product roles
 * that Material does not model: tabular numerals, spacing, touch targets, tonal elevation
 * levels, motion, and haptics.
 */
object CabTheme {
    val colorScheme: ColorScheme
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val extendedColors: CabExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalCabExtendedColors.current

    val typography: CabTypeScale
        @Composable @ReadOnlyComposable
        get() = LocalCabTypeScale.current

    val shapes: CabShapeScale
        @Composable @ReadOnlyComposable
        get() = LocalCabShapeScale.current

    val spacing: CabSpacing
        @Composable @ReadOnlyComposable
        get() = LocalCabSpacing.current

    val sizing: CabSizing
        @Composable @ReadOnlyComposable
        get() = LocalCabSizing.current

    val elevation: CabElevation
        @Composable @ReadOnlyComposable
        get() = LocalCabElevation.current

    val motion: CabMotion
        @Composable @ReadOnlyComposable
        get() = LocalCabMotion.current

    val haptics: CabHaptics
        @Composable @ReadOnlyComposable
        get() = LocalCabHaptics.current

    /** Default motion, useful outside composition (tests/previews of token values). */
    val defaultMotion: CabMotion
        get() = CabMotion.Default
}
