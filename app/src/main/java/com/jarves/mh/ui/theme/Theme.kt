package com.jarves.mh.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.runtime.remember
import android.content.res.AssetManager
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.jarves.mh.R
import com.jarves.mh.ui.AppStrings

/** @deprecated Use MaterialTheme.colorScheme.primary — kept as purple fallback only. */
val PocketOrange = Color(0xFFB39DDB)
val PocketBlue = Color(0xFF4C8DFF)
val PocketGreen = Color(0xFF2ECC71)
val PocketBackground = Color(0xFF0B0F14)
val PocketSurface = Color(0xFF131821)
val PocketSurfaceVariant = Color(0xFF1B222D)
val PocketOutline = Color(0xFF2A3340)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB39DDB),
    onPrimary = Color(0xFF241107),
    primaryContainer = Color(0xFF42281D),
    onPrimaryContainer = Color(0xFFFFDDCC),
    secondary = PocketBlue,
    onSecondary = Color(0xFF001F58),
    tertiary = PocketGreen,
    onTertiary = Color(0xFF00391E),
    background = PocketBackground,
    onBackground = Color(0xFFE6EDF3),
    surface = PocketSurface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = PocketSurfaceVariant,
    onSurfaceVariant = Color(0xFF9AA0A6),
    outline = PocketOutline,
    outlineVariant = Color(0xFF333B4A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9575CD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE0D2),
    onPrimaryContainer = Color(0xFF451A08),
    secondary = Color(0xFF3366CC),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF1B8A5A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEAEFF5),
    onSurfaceVariant = Color(0xFF57606A),
    outline = Color(0xFFD0D7DE),
    outlineVariant = Color(0xFFD8DEE4),
)


/**
 * Vazirmatn loaded from assets (reliable path) + res/font fallback weights.
 */
fun createVazirmatnFamily(assets: AssetManager): FontFamily = FontFamily(
    Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.Thin),
    Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.ExtraLight),
    Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.Light),
    Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.Normal),
    Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.W400),
    Font("fonts/Vazirmatn-Medium.ttf", assets, FontWeight.Medium),
    Font("fonts/Vazirmatn-Medium.ttf", assets, FontWeight.W500),
    Font("fonts/Vazirmatn-SemiBold.ttf", assets, FontWeight.SemiBold),
    Font("fonts/Vazirmatn-SemiBold.ttf", assets, FontWeight.W600),
    Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.Bold),
    Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.W700),
    Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.ExtraBold),
    Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.W800),
    Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.Black),
    Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.W900),
)

/** Keep for call sites that reference VazirmatnFamily without Context (res/font path). */
val VazirmatnFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Blocking),
    Font(R.font.vazirmatn_medium, FontWeight.Medium, loadingStrategy = FontLoadingStrategy.Blocking),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold, loadingStrategy = FontLoadingStrategy.Blocking),
    Font(R.font.vazirmatn_bold, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Blocking),
    Font(R.font.vazirmatn_regular, FontWeight.W400, loadingStrategy = FontLoadingStrategy.Blocking),
    Font(R.font.vazirmatn_medium, FontWeight.W500, loadingStrategy = FontLoadingStrategy.Blocking),
    Font(R.font.vazirmatn_semibold, FontWeight.W600, loadingStrategy = FontLoadingStrategy.Blocking),
    Font(R.font.vazirmatn_bold, FontWeight.W700, loadingStrategy = FontLoadingStrategy.Blocking),
    Font(R.font.vazirmatn_bold, FontWeight.W800, loadingStrategy = FontLoadingStrategy.Blocking),
    Font(R.font.vazirmatn_bold, FontWeight.W900, loadingStrategy = FontLoadingStrategy.Blocking),
)

private fun buildTypography(family: FontFamily): Typography {
    fun style(
        size: androidx.compose.ui.unit.TextUnit,
        lineHeight: androidx.compose.ui.unit.TextUnit,
        weight: FontWeight,
        letter: androidx.compose.ui.unit.TextUnit = 0.sp,
    ) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = lineHeight,
        letterSpacing = letter,
    )
    return Typography(
        displayLarge = style(57.sp, 64.sp, FontWeight.Normal),
        displayMedium = style(45.sp, 52.sp, FontWeight.Normal),
        displaySmall = style(36.sp, 44.sp, FontWeight.Normal),
        headlineLarge = style(32.sp, 40.sp, FontWeight.Normal),
        headlineMedium = style(28.sp, 36.sp, FontWeight.Normal),
        headlineSmall = style(24.sp, 32.sp, FontWeight.Normal),
        titleLarge = style(22.sp, 28.sp, FontWeight.Normal),
        titleMedium = style(16.sp, 24.sp, FontWeight.Medium, 0.15.sp),
        titleSmall = style(14.sp, 20.sp, FontWeight.Medium, 0.1.sp),
        bodyLarge = style(16.sp, 24.sp, FontWeight.Normal, 0.5.sp),
        bodyMedium = style(14.sp, 20.sp, FontWeight.Normal, 0.25.sp),
        bodySmall = style(12.sp, 16.sp, FontWeight.Normal, 0.4.sp),
        labelLarge = style(14.sp, 20.sp, FontWeight.Medium, 0.1.sp),
        labelMedium = style(12.sp, 16.sp, FontWeight.Medium, 0.5.sp),
        labelSmall = style(11.sp, 16.sp, FontWeight.Medium, 0.5.sp),
    )
}

enum class AccentColor(
    val id: String,
    val dark: Color,
    val light: Color,
) {
    DEFAULT("default", Color(0xFFB39DDB), Color(0xFF9575CD)),
    CLAUDE("claude", Color(0xFFD97757), Color(0xFFC45C3E)),
    DEEPSEEK("deepseek", Color(0xFF4C8DFF), Color(0xFF3366CC)),
    ANTIGRAVITY("antigravity", Color(0xFF34A853), Color(0xFF1B8A5A)),
    ORANGE("orange", Color(0xFFFF6B2C), Color(0xFFD85A20)),
    ;
    companion object {
        fun fromId(value: String?): AccentColor =
            entries.firstOrNull {
                it.id.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            } ?: DEFAULT
    }
}

enum class AppThemeMode { SYSTEM, DARK, LIGHT }

private fun darkSchemeFor(accent: Color) = DarkColors.copy(
    primary = accent,
    primaryContainer = accent.copy(alpha = 0.28f),
    onPrimaryContainer = Color(0xFFFFDDCC),
)

private fun lightSchemeFor(accent: Color) = LightColors.copy(
    primary = accent,
    primaryContainer = accent.copy(alpha = 0.18f),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFF1A1A1A),
)

@Composable
fun PocketTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    accentColor: AccentColor = AccentColor.DEFAULT,
    languageCode: String = "system",
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    // Asset-based family: does not depend on resource font inflation quirks.
    val family = remember(context.assets) { createVazirmatnFamily(context.assets) }
    val typography = remember(family) { buildTypography(family) }
    val accent = if (isDark) accentColor.dark else accentColor.light

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = if (isDark) darkSchemeFor(accent) else lightSchemeFor(accent),
        typography = typography,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides typography.bodyLarge,
        ) {
            content()
        }
    }
}
