package com.semmyx.ledger.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semmyx.ledger.domain.AppLanguage
import com.semmyx.ledger.domain.ThemeMode
import java.util.Locale

// ---------------------------------------------------------------------------
// Premium color palette
// Light: warm white surfaces + deep indigo primary + emerald income
// Dark : pure OLED black + soft periwinkle + vivid emerald — no grey wash
// ---------------------------------------------------------------------------

private val LightColors = lightColorScheme(
    primary            = Color(0xFF3D5AFE),   // Indigo A400 — vibrant but not garish
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFE8EAFF),
    onPrimaryContainer = Color(0xFF001480),

    secondary            = Color(0xFF00875A),   // Emerald — income
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFD7F5E8),
    onSecondaryContainer = Color(0xFF00391F),

    tertiary            = Color(0xFFFF5252),    // Crimson — expense
    onTertiary          = Color.White,
    tertiaryContainer   = Color(0xFFFFDEDE),
    onTertiaryContainer = Color(0xFF7F0000),

    background   = Color(0xFFF5F6FF),           // Slightly blue-tinted white
    onBackground = Color(0xFF0D0E1A),

    surface        = Color(0xFFFFFFFF),
    onSurface      = Color(0xFF0D0E1A),
    surfaceVariant = Color(0xFFEDEEFF),
    onSurfaceVariant = Color(0xFF4A4C6B),

    outline        = Color(0xFF9496B4),
    outlineVariant = Color(0xFFD8D9F0),

    error   = Color(0xFFB00020),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary            = Color(0xFF8EA7FF),   // Soft periwinkle on pure black
    onPrimary          = Color(0xFF001480),
    primaryContainer   = Color(0xFF1A237E),
    onPrimaryContainer = Color(0xFFCDD5FF),

    secondary            = Color(0xFF2ECC71),   // Bright emerald
    onSecondary          = Color(0xFF00391F),
    secondaryContainer   = Color(0xFF00512E),
    onSecondaryContainer = Color(0xFFA7F3C6),

    tertiary            = Color(0xFFFF6E6E),    // Bright red
    onTertiary          = Color(0xFF7F0000),
    tertiaryContainer   = Color(0xFF5C0000),
    onTertiaryContainer = Color(0xFFFFCDD2),

    background   = Color(0xFF000000),           // Pure OLED
    onBackground = Color(0xFFF0F0FF),

    surface        = Color(0xFF111218),         // Very dark blue-grey
    onSurface      = Color(0xFFF0F0FF),
    surfaceVariant = Color(0xFF1C1E30),
    onSurfaceVariant = Color(0xFFB0B3D8),

    outline        = Color(0xFF5C5E80),
    outlineVariant = Color(0xFF2A2C46),

    error   = Color(0xFFFF6680),
    onError = Color(0xFF4A0010),
)

// ---------------------------------------------------------------------------
// Typography — slightly larger line-heights prevent text clipping
// ---------------------------------------------------------------------------

private val LedgerTypography = Typography(
    displayLarge  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 45.sp, lineHeight = 52.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 42.sp),
    headlineMedium= TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp),
    bodyMedium    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp),
    bodySmall     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
    labelLarge    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

// ---------------------------------------------------------------------------
// Premium shape scale — softer corners everywhere
// ---------------------------------------------------------------------------

private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ---------------------------------------------------------------------------
// Theme entry point
// ---------------------------------------------------------------------------

@Composable
fun LedgerTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    overrides: Map<String, String> = emptyMap(),
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
    }
    val palette = remember(overrides, dark) {
        AppearancePalette(
            overrides,
            if (dark) emptyMap()
            else mapOf(
                "background" to "#F5F6FF",
                "surface"    to "#FFFFFF",
                "text"       to "#0D0E1A",
                "muted"      to "#4A4C6B",
            ),
        )
    }
    val base   = if (dark) DarkColors else LightColors
    val accent = palette.color("accent")
    val scheme = base.copy(
        primary              = accent,
        onPrimary            = readableOn(accent),
        primaryContainer     = accent.copy(alpha = .18f),
        onPrimaryContainer   = accent,
        secondary            = palette.color("income"),
        onSecondary          = readableOn(palette.color("income")),
        secondaryContainer   = palette.color("income").copy(alpha = .18f),
        onSecondaryContainer = palette.color("income"),
        surfaceTint          = accent,
        error                = palette.color("error"),
        onError              = readableOn(palette.color("error")),
        tertiaryContainer    = palette.color("expense").copy(alpha = .18f),
        onTertiaryContainer  = palette.color("expense"),
        tertiary             = palette.color("expense"),
        onTertiary           = readableOn(palette.color("expense")),
        background           = if (dark || overrides.containsKey("background")) palette.color("background") else base.background,
        surface              = if (dark || overrides.containsKey("surface"))    palette.color("surface")    else base.surface,
        onBackground         = if (dark || overrides.containsKey("text"))       palette.color("text")       else base.onBackground,
        onSurface            = if (dark || overrides.containsKey("text"))       palette.color("text")       else base.onSurface,
        surfaceVariant       = if (dark || overrides.containsKey("surface"))    palette.color("surface")    else base.surfaceVariant,
        onSurfaceVariant     = if (dark || overrides.containsKey("muted"))      palette.color("muted")      else base.onSurfaceVariant,
        outline              = palette.color("grid"),
        outlineVariant       = palette.color("grid"),
    )
    CompositionLocalProvider(LocalAppearance provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography  = LedgerTypography,
            shapes      = LedgerShapes,
            content     = content,
        )
    }
}

// ---------------------------------------------------------------------------
// Localization
// ---------------------------------------------------------------------------

/**
 * Compose-scoped configuration avoids Activity.recreate() and a process restart.
 * DataStore is the single source of truth. LocalContext + LocalConfiguration
 * invalidate stringResource, date formatting, dialogs and accessibility labels.
 * Both supported languages are LTR. No global Locale.setDefault mutation.
 */
@Composable
fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val base     = LocalContext.current
    val original = LocalConfiguration.current
    val config   = remember(original, language) {
        Configuration(original).apply { setLocale(Locale.forLanguageTag(language.tag)) }
    }
    val localizedContext = remember(base, config) { base.createConfigurationContext(config) }
    CompositionLocalProvider(
        LocalContext          provides localizedContext,
        LocalConfiguration   provides config,
        LocalLayoutDirection  provides LayoutDirection.Ltr,
        content               = content,
    )
}
