package dev.opencab.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * OpenCab's branded palette.
 *
 * The visual direction is a precision instrument panel: graphite and cool paper surfaces,
 * a single instrument-blue accent, and signal colours reserved for status. There is no
 * skeuomorphic wood, brass, or leather here. Saturation is deliberately low so that the
 * status colours and the speed numerals carry the hierarchy.
 */

// Light branded scheme -------------------------------------------------------------------------

internal val CabBlueLight = Color(0xFF005A78)
internal val CabTealLight = Color(0xFF4A5F66)
internal val CabAmberLight = Color(0xFF755B00)
internal val CabRedLight = Color(0xFFBB1614)

// Dark branded scheme --------------------------------------------------------------------------

internal val CabBlueDark = Color(0xFF8ED5F8)
internal val CabTealDark = Color(0xFFB3CBD2)
internal val CabAmberDark = Color(0xFFF8C64E)
internal val CabRedDark = Color(0xFFFFB4AB)

internal val LightColorScheme: ColorScheme =
    lightColorScheme(
        primary = CabBlueLight,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBFE9FF),
        onPrimaryContainer = Color(0xFF001E2B),
        inversePrimary = Color(0xFF8ED5F8),
        secondary = CabTealLight,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCDE7EF),
        onSecondaryContainer = Color(0xFF0B1F24),
        tertiary = CabAmberLight,
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE08C),
        onTertiaryContainer = Color(0xFF241A00),
        background = Color(0xFFF8F9FB),
        onBackground = Color(0xFF17191B),
        surface = Color(0xFFF8F9FB),
        onSurface = Color(0xFF17191B),
        surfaceVariant = Color(0xFFDDE3E7),
        onSurfaceVariant = Color(0xFF3F484C),
        surfaceTint = CabBlueLight,
        inverseSurface = Color(0xFF2C3133),
        inverseOnSurface = Color(0xFFEFF1F2),
        error = CabRedLight,
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF6E767B),
        outlineVariant = Color(0xFFBEC6CA),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFD8DADC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF2F3F5),
        surfaceContainer = Color(0xFFECEEF0),
        surfaceContainerHigh = Color(0xFFE6E8EA),
        surfaceContainerHighest = Color(0xFFE0E2E4),
    )

internal val DarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = CabBlueDark,
        onPrimary = Color(0xFF00344A),
        primaryContainer = Color(0xFF004C66),
        onPrimaryContainer = Color(0xFFC4E7FF),
        inversePrimary = Color(0xFF005A78),
        secondary = CabTealDark,
        onSecondary = Color(0xFF1D3138),
        secondaryContainer = Color(0xFF344A51),
        onSecondaryContainer = Color(0xFFCDE7EF),
        tertiary = CabAmberDark,
        onTertiary = Color(0xFF3F2E00),
        tertiaryContainer = Color(0xFF5C4300),
        onTertiaryContainer = Color(0xFFFFE08C),
        background = Color(0xFF0E1113),
        onBackground = Color(0xFFE3E5E7),
        surface = Color(0xFF0E1113),
        onSurface = Color(0xFFE3E5E7),
        surfaceVariant = Color(0xFF3F484C),
        onSurfaceVariant = Color(0xFFD8DEE2),
        surfaceTint = CabBlueDark,
        inverseSurface = Color(0xFFE3E5E7),
        inverseOnSurface = Color(0xFF2C3133),
        error = CabRedDark,
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8A9297),
        outlineVariant = Color(0xFF3F484C),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF34393B),
        surfaceDim = Color(0xFF0E1113),
        surfaceContainerLowest = Color(0xFF090C0D),
        surfaceContainerLow = Color(0xFF161A1C),
        surfaceContainer = Color(0xFF1B1F21),
        surfaceContainerHigh = Color(0xFF25292B),
        surfaceContainerHighest = Color(0xFF303436),
    )

// High-contrast branded schemes ----------------------------------------------------------------

internal val LightHighContrastColorScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF003F57),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD3EDFF),
        onPrimaryContainer = Color(0xFF000000),
        inversePrimary = Color(0xFF9AD9FF),
        secondary = Color(0xFF2E444B),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD6ECF3),
        onSecondaryContainer = Color(0xFF000000),
        tertiary = Color(0xFF5A4400),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFE08C),
        onTertiaryContainer = Color(0xFF000000),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF000000),
        surfaceVariant = Color(0xFFE3E6E8),
        onSurfaceVariant = Color(0xFF111416),
        surfaceTint = Color(0xFF003F57),
        inverseSurface = Color(0xFF1A1D1F),
        inverseOnSurface = Color(0xFFFFFFFF),
        error = Color(0xFF8F0009),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF000000),
        outline = Color(0xFF3A4246),
        outlineVariant = Color(0xFF9AA2A6),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFD9DBDD),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F5F6),
        surfaceContainer = Color(0xFFEDEFF0),
        surfaceContainerHigh = Color(0xFFE5E8E9),
        surfaceContainerHighest = Color(0xFFDDE0E2),
    )

internal val DarkHighContrastColorScheme: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFB5E4FF),
        onPrimary = Color(0xFF001F2E),
        primaryContainer = Color(0xFF003A4F),
        onPrimaryContainer = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFF00546F),
        secondary = Color(0xFFD0E9F0),
        onSecondary = Color(0xFF0A2026),
        secondaryContainer = Color(0xFF2C4249),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFFFFD36B),
        onTertiary = Color(0xFF2E2100),
        tertiaryContainer = Color(0xFF4D3800),
        onTertiaryContainer = Color(0xFFFFFFFF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF2A3236),
        onSurfaceVariant = Color(0xFFF0F3F5),
        surfaceTint = Color(0xFFB5E4FF),
        inverseSurface = Color(0xFFFFFFFF),
        inverseOnSurface = Color(0xFF0A0D0E),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF5A0003),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFFFFF),
        outline = Color(0xFFAEB6BA),
        outlineVariant = Color(0xFF5A6266),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF3A3F41),
        surfaceDim = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0C0F10),
        surfaceContainer = Color(0xFF121617),
        surfaceContainerHigh = Color(0xFF1C2123),
        surfaceContainerHighest = Color(0xFF262B2D),
    )

// Semantic colours -----------------------------------------------------------------------------
//
// Status is never encoded by colour alone, but the colour still has to stay stable when
// dynamic colour is enabled. These values are deliberately outside the dynamic palette.

@Immutable
data class CabExtendedColors(
    val live: Color,
    val liveContainer: Color,
    val onLiveContainer: Color,
    val caution: Color,
    val cautionContainer: Color,
    val onCautionContainer: Color,
    val offline: Color,
    val offlineContainer: Color,
    val onOfflineContainer: Color,
    val stop: Color,
    val onStop: Color,
    val disabledContent: Color,
)

internal val LightExtendedColors: CabExtendedColors =
    CabExtendedColors(
        live = Color(0xFF0B6B4F),
        liveContainer = Color(0xFFC6F0DD),
        onLiveContainer = Color(0xFF002014),
        caution = Color(0xFF7A5900),
        cautionContainer = Color(0xFFFFE08C),
        onCautionContainer = Color(0xFF241A00),
        offline = Color(0xFFB3261E),
        offlineContainer = Color(0xFFFFDAD6),
        onOfflineContainer = Color(0xFF410002),
        stop = CabRedLight,
        onStop = Color(0xFFFFFFFF),
        disabledContent = Color(0xFF6F767A),
    )

internal val DarkExtendedColors: CabExtendedColors =
    CabExtendedColors(
        live = Color(0xFF6FDBAF),
        liveContainer = Color(0xFF00513A),
        onLiveContainer = Color(0xFFA8F2D3),
        caution = Color(0xFFF8C64E),
        cautionContainer = Color(0xFF5C4300),
        onCautionContainer = Color(0xFFFFE08C),
        offline = Color(0xFFFFB4AB),
        offlineContainer = Color(0xFF93000A),
        onOfflineContainer = Color(0xFFFFDAD6),
        stop = Color(0xFFB3261E),
        onStop = Color(0xFFFFFFFF),
        disabledContent = Color(0xFF8A9297),
    )

internal val LightHighContrastExtendedColors: CabExtendedColors =
    LightExtendedColors.copy(
        live = Color(0xFF004D37),
        liveContainer = Color(0xFFC6F0DD),
        onLiveContainer = Color(0xFF000000),
        caution = Color(0xFF5A4400),
        cautionContainer = Color(0xFFFFE08C),
        onCautionContainer = Color(0xFF000000),
        offline = Color(0xFF8F0009),
        offlineContainer = Color(0xFFFFDAD6),
        onOfflineContainer = Color(0xFF000000),
        stop = Color(0xFF8F0009),
        onStop = Color(0xFFFFFFFF),
        disabledContent = Color(0xFF3A4246),
    )

internal val DarkHighContrastExtendedColors: CabExtendedColors =
    DarkExtendedColors.copy(
        live = Color(0xFF8FFFD1),
        liveContainer = Color(0xFF003B2A),
        onLiveContainer = Color(0xFFFFFFFF),
        caution = Color(0xFFFFD36B),
        cautionContainer = Color(0xFF4D3800),
        onCautionContainer = Color(0xFFFFFFFF),
        offline = Color(0xFFFFB4AB),
        offlineContainer = Color(0xFF6E0006),
        onOfflineContainer = Color(0xFFFFFFFF),
        stop = Color(0xFFC62828),
        onStop = Color(0xFFFFFFFF),
        disabledContent = Color(0xFFAEB6BA),
    )
