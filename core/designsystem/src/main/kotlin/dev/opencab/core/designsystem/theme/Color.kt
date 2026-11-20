package dev.opencab.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val OpenCabBlue = Color(0xFF0B5C8A)
internal val OpenCabBlueDark = Color(0xFF8FCFF5)
internal val OpenCabTeal = Color(0xFF00696E)
internal val OpenCabAmber = Color(0xFF8A5100)

internal val LightColorScheme =
    lightColorScheme(
        primary = OpenCabBlue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC9E6FF),
        onPrimaryContainer = Color(0xFF001E30),
        secondary = OpenCabTeal,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF9CF1F7),
        onSecondaryContainer = Color(0xFF002022),
        tertiary = OpenCabAmber,
        onTertiary = Color.White,
        background = Color(0xFFFAFAFF),
        onBackground = Color(0xFF191C1E),
        surface = Color(0xFFFAFAFF),
        onSurface = Color(0xFF191C1E),
        surfaceVariant = Color(0xFFDDE3EA),
        onSurfaceVariant = Color(0xFF41474D),
        outline = Color(0xFF71787E),
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = OpenCabBlueDark,
        onPrimary = Color(0xFF00344E),
        primaryContainer = Color(0xFF004C6E),
        onPrimaryContainer = Color(0xFFC9E6FF),
        secondary = Color(0xFF80D4DA),
        onSecondary = Color(0xFF003739),
        secondaryContainer = Color(0xFF004F53),
        onSecondaryContainer = Color(0xFF9CF1F7),
        tertiary = Color(0xFFFFB95C),
        onTertiary = Color(0xFF482900),
        background = Color(0xFF101416),
        onBackground = Color(0xFFE1E3E5),
        surface = Color(0xFF101416),
        onSurface = Color(0xFFE1E3E5),
        surfaceVariant = Color(0xFF41474D),
        onSurfaceVariant = Color(0xFFC0C7CE),
        outline = Color(0xFF8B9198),
    )
