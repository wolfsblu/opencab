package dev.opencab.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Material roles used by stock M3 components.
 *
 * Numeric readouts do not use these styles directly; see [CabTypeScale] for the tabular
 * variants that keep speed and address digits from shifting as values change.
 */
internal val CabMaterialTypography: Typography =
    Typography(
        displayLarge =
        TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 64.sp,
            lineHeight = 72.sp,
            letterSpacing = (-1.5).sp,
        ),
        displayMedium =
        TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 52.sp,
            lineHeight = 60.sp,
            letterSpacing = (-1).sp,
        ),
        displaySmall =
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 44.sp,
            lineHeight = 52.sp,
            letterSpacing = (-0.5).sp,
        ),
        headlineLarge =
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 36.sp,
            lineHeight = 44.sp,
        ),
        headlineMedium =
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 30.sp,
            lineHeight = 38.sp,
        ),
        headlineSmall =
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
        ),
        titleLarge =
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        titleMedium =
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        titleSmall =
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge =
        TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 17.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.15.sp,
        ),
        bodyMedium =
        TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall =
        TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.4.sp,
        ),
        labelLarge =
        TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium =
        TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall =
        TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
    )

/**
 * OpenCab's extended type roles.
 *
 * Every numeric role uses `tnum` so the digits are tabular: a speed changing from 11 to 88 or
 * an address from 1111 to 8888 never shifts horizontally while the operator is reading it.
 */
@Immutable
data class CabTypeScale(
    val speedValue: TextStyle,
    val speedUnit: TextStyle,
    val speedCaption: TextStyle,
    val address: TextStyle,
    val readout: TextStyle,
    val readoutSmall: TextStyle,
    val functionKey: TextStyle,
    val status: TextStyle,
    val sectionTitle: TextStyle,
) {
    companion object {
        val Default: CabTypeScale =
            CabTypeScale(
                speedValue = tabularNumeric(88.sp, 96.sp, FontWeight.Bold, (-2).sp),
                speedUnit = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.5.sp,
                ),
                speedCaption =
                TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.5.sp,
                ),
                address = tabularNumeric(22.sp, 28.sp, FontWeight.Bold, 0.sp),
                readout = tabularNumeric(17.sp, 26.sp, FontWeight.Normal, 0.15.sp),
                readoutSmall = tabularNumeric(13.sp, 16.sp, FontWeight.Medium, 0.5.sp),
                functionKey =
                TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.2.sp,
                ),
                status =
                TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.4.sp,
                ),
                sectionTitle =
                TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    letterSpacing = 0.1.sp,
                ),
            )

        private fun tabularNumeric(
            size: TextUnit,
            lineHeight: TextUnit,
            weight: FontWeight,
            tracking: TextUnit,
        ): TextStyle = TextStyle(
            fontWeight = weight,
            fontSize = size,
            lineHeight = lineHeight,
            letterSpacing = tracking,
            fontFeatureSettings = "tnum",
        )
    }
}

internal val LocalCabTypeScale = staticCompositionLocalOf { CabTypeScale.Default }
