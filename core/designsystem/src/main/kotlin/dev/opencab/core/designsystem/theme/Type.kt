package dev.opencab.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val OpenCabTypography =
    Typography(
        displayLarge =
        TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 72.sp,
            lineHeight = 80.sp,
            letterSpacing = (-2).sp,
        ),
        headlineMedium =
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
        ),
        titleLarge =
        TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
        ),
        labelLarge =
        TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
    )
