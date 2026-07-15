package org.orynnx.codexquota

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

object QuotaColors {
    val CanvasLight = Color(0xFFF7F7F5)
    val SurfaceLight = Color(0xFFFFFFFF)
    val MutedLight = Color(0xFFF0F0EC)
    val InkLight = Color(0xFF10100F)
    val SecondaryLight = Color(0xFF6F6F69)
    val HairlineLight = Color(0xFFE2E2DC)

    val CanvasDark = Color(0xFF0F0F0E)
    val SurfaceDark = Color(0xFF181817)
    val MutedDark = Color(0xFF20201F)
    val InkDark = Color(0xFFF4F4F0)
    val SecondaryDark = Color(0xFFA6A69F)
    val HairlineDark = Color(0xFF30302D)

    val Success = Color(0xFF10A37F)
    val Warning = Color(0xFFC77A10)
    val Error = Color(0xFFD34C43)
}

private val LightScheme = lightColorScheme(
    primary = QuotaColors.InkLight,
    onPrimary = Color.White,
    background = QuotaColors.CanvasLight,
    onBackground = QuotaColors.InkLight,
    surface = QuotaColors.SurfaceLight,
    onSurface = QuotaColors.InkLight,
    surfaceVariant = QuotaColors.MutedLight,
    onSurfaceVariant = QuotaColors.SecondaryLight,
    outline = QuotaColors.HairlineLight,
    error = QuotaColors.Error,
)

private val DarkScheme = darkColorScheme(
    primary = QuotaColors.InkDark,
    onPrimary = QuotaColors.InkLight,
    background = QuotaColors.CanvasDark,
    onBackground = QuotaColors.InkDark,
    surface = QuotaColors.SurfaceDark,
    onSurface = QuotaColors.InkDark,
    surfaceVariant = QuotaColors.MutedDark,
    onSurfaceVariant = QuotaColors.SecondaryDark,
    outline = QuotaColors.HairlineDark,
    error = QuotaColors.Error,
)

private val QuotaTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 68.sp, lineHeight = 68.sp, letterSpacing = (-2).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
)

private val QuotaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun OuterViewQuotaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = QuotaTypography,
        shapes = QuotaShapes,
        content = content,
    )
}
