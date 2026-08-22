package com.nickwoluff.credex

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController

object QuotaColors {
    val Success = Color(0xFF10A37F)
    val Warning = Color(0xFFC77A10)
    val Error = Color(0xFFD34C43)
}

// 这里的值对应 Miuix 官方 Colors.lightColorScheme()/darkColorScheme() 默认值。
// 项目仍使用 Material 组件承载现有页面，因此将同一套 Miuix 语义色映射到 Material roles。
private val MiuixLightScheme = lightColorScheme(
    primary = Color(0xFF3482FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5D9BFF),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFE6E6E6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color(0xFFA9A9A9),
    tertiary = Color(0xFF3482FF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAF2FF),
    onTertiaryContainer = Color(0xFF3482FF),
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF8C93B0),
    outline = Color(0xFFD9D9D9),
    error = QuotaColors.Error,
)

private val MiuixDarkScheme = darkColorScheme(
    primary = Color(0xFF277AF7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF338FE4),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF505050),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF434343),
    onSecondaryContainer = Color(0xFF7C7C7C),
    tertiary = Color(0xFF4788FF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2B3B54),
    onTertiaryContainer = Color(0xFF4788FF),
    background = Color(0xFF242424),
    onBackground = Color(0xFFE6FFFFFF),
    surface = Color.Black,
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFF787E96),
    outline = Color(0xFF404040),
    error = QuotaColors.Error,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CredexTheme(
    style: UiStyle = UiStyle.MATERIAL,
    dynamicColor: Boolean = true,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    materialAccent: MaterialAccent = MaterialAccent.BLUE,
    materialPaletteStyle: MaterialPaletteStyle = MaterialPaletteStyle.TONAL_SPOT,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    if (style == UiStyle.MATERIAL) {
        val colorScheme = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                remember(context, dark, materialPaletteStyle) {
                    dynamicMaterialColorScheme(context, materialPaletteStyle, dark)
                }
            }
            else -> remember(dark, materialAccent, materialPaletteStyle) {
                customMaterialColorScheme(materialAccent, materialPaletteStyle, dark)
            }
        }
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    } else {
        // 真实 Miuix 主题提供颜色、波纹和过滚动行为；MaterialTheme 仅作为
        // 尚未迁移的余额业务控件的兼容层。
        val miuixMode = when (themeMode) {
            ThemeMode.SYSTEM -> ColorSchemeMode.System
            ThemeMode.LIGHT -> ColorSchemeMode.Light
            ThemeMode.DARK -> ColorSchemeMode.Dark
        }
        val controller = remember(miuixMode) { ThemeController(miuixMode) }
        MiuixTheme(controller = controller) {
            MaterialTheme(
                colorScheme = if (dark) MiuixDarkScheme else MiuixLightScheme,
                content = content,
            )
        }
    }
}

private fun customMaterialColorScheme(
    accent: MaterialAccent,
    paletteStyle: MaterialPaletteStyle,
    dark: Boolean,
) = generatedMaterialColorScheme(
    sourceArgb = when (accent) {
            MaterialAccent.BLUE -> 0xFF386AFA.toInt()
            MaterialAccent.PURPLE -> 0xFF7B57D1.toInt()
            MaterialAccent.GREEN -> 0xFF0F7B5B.toInt()
            MaterialAccent.ORANGE -> 0xFF9A5A00.toInt()
            MaterialAccent.RED -> 0xFFB3261E.toInt()
        },
    paletteStyle = paletteStyle,
    dark = dark,
)

private fun dynamicMaterialColorScheme(
    context: android.content.Context,
    paletteStyle: MaterialPaletteStyle,
    dark: Boolean,
): androidx.compose.material3.ColorScheme {
    val sourceArgb = context.getColor(android.R.color.system_accent1_500)
    return generatedMaterialColorScheme(sourceArgb, paletteStyle, dark)
}

private fun generatedMaterialColorScheme(
    sourceArgb: Int,
    paletteStyle: MaterialPaletteStyle,
    dark: Boolean,
) = MaterialDynamicColors().let { roles ->
    val source = Hct.fromInt(sourceArgb)
    val scheme: DynamicScheme = when (paletteStyle) {
        MaterialPaletteStyle.TONAL_SPOT -> SchemeTonalSpot(source, dark, 0.0)
        MaterialPaletteStyle.VIBRANT -> SchemeVibrant(source, dark, 0.0)
        MaterialPaletteStyle.EXPRESSIVE -> SchemeExpressive(source, dark, 0.0)
        MaterialPaletteStyle.NEUTRAL -> SchemeNeutral(source, dark, 0.0)
    }
    fun role(color: com.google.android.material.color.utilities.DynamicColor) = Color(color.getArgb(scheme))
    val values = mapOf(
        "primary" to role(roles.primary()),
        "onPrimary" to role(roles.onPrimary()),
        "primaryContainer" to role(roles.primaryContainer()),
        "onPrimaryContainer" to role(roles.onPrimaryContainer()),
        "inversePrimary" to role(roles.inversePrimary()),
        "secondary" to role(roles.secondary()),
        "onSecondary" to role(roles.onSecondary()),
        "secondaryContainer" to role(roles.secondaryContainer()),
        "onSecondaryContainer" to role(roles.onSecondaryContainer()),
        "tertiary" to role(roles.tertiary()),
        "onTertiary" to role(roles.onTertiary()),
        "tertiaryContainer" to role(roles.tertiaryContainer()),
        "onTertiaryContainer" to role(roles.onTertiaryContainer()),
        "background" to role(roles.background()),
        "onBackground" to role(roles.onBackground()),
        "surface" to role(roles.surface()),
        "onSurface" to role(roles.onSurface()),
        "surfaceVariant" to role(roles.surfaceVariant()),
        "onSurfaceVariant" to role(roles.onSurfaceVariant()),
        "surfaceTint" to role(roles.surfaceTint()),
        "inverseSurface" to role(roles.inverseSurface()),
        "inverseOnSurface" to role(roles.inverseOnSurface()),
        "error" to role(roles.error()),
        "onError" to role(roles.onError()),
        "errorContainer" to role(roles.errorContainer()),
        "onErrorContainer" to role(roles.onErrorContainer()),
        "outline" to role(roles.outline()),
        "outlineVariant" to role(roles.outlineVariant()),
        "scrim" to role(roles.scrim()),
        "surfaceBright" to role(roles.surfaceBright()),
        "surfaceContainer" to role(roles.surfaceContainer()),
        "surfaceContainerHigh" to role(roles.surfaceContainerHigh()),
        "surfaceContainerHighest" to role(roles.surfaceContainerHighest()),
        "surfaceContainerLow" to role(roles.surfaceContainerLow()),
        "surfaceContainerLowest" to role(roles.surfaceContainerLowest()),
        "surfaceDim" to role(roles.surfaceDim()),
    )
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = values.getValue("primary"),
        onPrimary = values.getValue("onPrimary"),
        primaryContainer = values.getValue("primaryContainer"),
        onPrimaryContainer = values.getValue("onPrimaryContainer"),
        inversePrimary = values.getValue("inversePrimary"),
        secondary = values.getValue("secondary"),
        onSecondary = values.getValue("onSecondary"),
        secondaryContainer = values.getValue("secondaryContainer"),
        onSecondaryContainer = values.getValue("onSecondaryContainer"),
        tertiary = values.getValue("tertiary"),
        onTertiary = values.getValue("onTertiary"),
        tertiaryContainer = values.getValue("tertiaryContainer"),
        onTertiaryContainer = values.getValue("onTertiaryContainer"),
        background = values.getValue("background"),
        onBackground = values.getValue("onBackground"),
        surface = values.getValue("surface"),
        onSurface = values.getValue("onSurface"),
        surfaceVariant = values.getValue("surfaceVariant"),
        onSurfaceVariant = values.getValue("onSurfaceVariant"),
        surfaceTint = values.getValue("surfaceTint"),
        inverseSurface = values.getValue("inverseSurface"),
        inverseOnSurface = values.getValue("inverseOnSurface"),
        error = values.getValue("error"),
        onError = values.getValue("onError"),
        errorContainer = values.getValue("errorContainer"),
        onErrorContainer = values.getValue("onErrorContainer"),
        outline = values.getValue("outline"),
        outlineVariant = values.getValue("outlineVariant"),
        scrim = values.getValue("scrim"),
        surfaceBright = values.getValue("surfaceBright"),
        surfaceContainer = values.getValue("surfaceContainer"),
        surfaceContainerHigh = values.getValue("surfaceContainerHigh"),
        surfaceContainerHighest = values.getValue("surfaceContainerHighest"),
        surfaceContainerLow = values.getValue("surfaceContainerLow"),
        surfaceContainerLowest = values.getValue("surfaceContainerLowest"),
        surfaceDim = values.getValue("surfaceDim"),
    )
}
