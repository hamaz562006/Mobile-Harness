package com.jarves.mh.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.jarves.mh.R
import com.jarves.mh.ui.AppStrings

val PocketOrange = Color(0xFFFF6B2C)
val PocketBlue = Color(0xFF4C8DFF)
val PocketGreen = Color(0xFF2ECC71)
val PocketBackground = Color(0xFF0B0F14)
val PocketSurface = Color(0xFF131821)
val PocketSurfaceVariant = Color(0xFF1B222D)
val PocketOutline = Color(0xFF2A3340)

private val DarkColors = darkColorScheme(
    primary = PocketOrange,
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
    primary = Color(0xFFD85A20),
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

private val VazirmatnFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun appTypography(useVazirmatn: Boolean): Typography {
    val base = Typography()
    if (!useVazirmatn) return base
    fun TextStyle.withVazir() = copy(fontFamily = VazirmatnFamily)
    return Typography(
        displayLarge = base.displayLarge.withVazir(),
        displayMedium = base.displayMedium.withVazir(),
        displaySmall = base.displaySmall.withVazir(),
        headlineLarge = base.headlineLarge.withVazir(),
        headlineMedium = base.headlineMedium.withVazir(),
        headlineSmall = base.headlineSmall.withVazir(),
        titleLarge = base.titleLarge.withVazir(),
        titleMedium = base.titleMedium.withVazir(),
        titleSmall = base.titleSmall.withVazir(),
        bodyLarge = base.bodyLarge.withVazir(),
        bodyMedium = base.bodyMedium.withVazir(),
        bodySmall = base.bodySmall.withVazir(),
        labelLarge = base.labelLarge.withVazir(),
        labelMedium = base.labelMedium.withVazir(),
        labelSmall = base.labelSmall.withVazir(),
    )
}

/**
 * Accent colors inspired by coding-agent brand hues.
 * Drives MaterialTheme.primary; more appearance options can hang off this later.
 */
enum class AccentColor(
    val id: String,
    val dark: Color,
    val light: Color,
) {
    /** App default — current Pocket orange */
    DEFAULT("default", Color(0xFFFF6B2C), Color(0xFFD85A20)),
    /** Claude / Anthropic warm terracotta */
    CLAUDE("claude", Color(0xFFD97757), Color(0xFFC45C3E)),
    /** DeepSeek blue */
    DEEPSEEK("deepseek", Color(0xFF4C8DFF), Color(0xFF3366CC)),
    /** Antigravity / Google green */
    ANTIGRAVITY("antigravity", Color(0xFF34A853), Color(0xFF1B8A5A)),
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
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val languageCode = AppStrings.languageCode(LocalContext.current)
    val useVazirmatn = languageCode.equals("fa", ignoreCase = true)
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
        typography = appTypography(useVazirmatn),
        content = content,
    )
}
