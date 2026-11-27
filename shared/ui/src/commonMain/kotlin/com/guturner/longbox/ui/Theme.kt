package com.guturner.longbox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Palette ───────────────────────────────────────────────────────────────────

// Light — "Retro Paper": warm ivory, navy ink, violet accent.
private val LightBackground   = Color(0xFFF4EFE4)
private val LightSurface      = Color(0xFFE5DBC8)
private val LightSurfaceHigh  = Color(0xFFFFFCF5)
private val LightOnSurface    = Color(0xFF1D2433)
private val LightOnSurfaceSec = Color(0xFF626477)
private val LightOutline      = Color(0xFFC8BDAA)
private val LightPrimary      = Color(0xFF6757D9)
private val LightOnPrimary    = Color(0xFFFFFFFF)

// Dark — "Neon Archive": deep navy, near-white text, neon violet accent.
private val DarkBackground   = Color(0xFF101522)
private val DarkSurface      = Color(0xFF1A2030)
private val DarkSurfaceHigh  = Color(0xFF20283A)
private val DarkOnSurface    = Color(0xFFF3F2FF)
private val DarkOnSurfaceSec = Color(0xFFA2A9BD)
private val DarkOutline      = Color(0xFF354057)
private val DarkPrimary      = Color(0xFF9A83FF)
private val DarkOnPrimary    = Color(0xFF1A1030)

private val LightColors = lightColorScheme(
    primary            = LightPrimary,
    onPrimary          = LightOnPrimary,
    primaryContainer   = Color(0xFFE8E4FF),
    onPrimaryContainer = Color(0xFF1A0070),
    background         = LightBackground,
    onBackground       = LightOnSurface,
    surface            = LightSurface,
    onSurface          = LightOnSurface,
    surfaceVariant     = LightSurfaceHigh,
    onSurfaceVariant   = LightOnSurfaceSec,
    outline            = LightOutline,
    error              = Color(0xFFFF514F),
    onError            = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary            = DarkPrimary,
    onPrimary          = DarkOnPrimary,
    primaryContainer   = Color(0xFF3D2FAA),
    onPrimaryContainer = Color(0xFFE6DCFF),
    background         = DarkBackground,
    onBackground       = DarkOnSurface,
    surface            = DarkSurface,
    onSurface          = DarkOnSurface,
    surfaceVariant     = DarkSurfaceHigh,
    onSurfaceVariant   = DarkOnSurfaceSec,
    outline            = DarkOutline,
    error              = Color(0xFFFF6E6C),
    onError            = Color(0xFF690005),
)

// ── Rainbow ───────────────────────────────────────────────────────────────────

/**
 * The Longbox rainbow: 7 saturated signature colors used for series accents,
 * container spines, and decorative elements. Assign by index modulo 7.
 */
val LongboxRainbow: List<Color> = listOf(
    Color(0xFF7757F6), // Electric Violet
    Color(0xFF268BFF), // Digital Blue
    Color(0xFF00CFA5), // Retro Green
    Color(0xFFFFD43B), // Arcade Yellow
    Color(0xFFFF9A32), // Tangerine
    Color(0xFFFF514F), // Comic Red
    Color(0xFFF04FA6), // Neon Pink
)

/** Pick a stable rainbow color for any integer key (series id, index, etc.). */
fun rainbowFor(key: Int): Color = LongboxRainbow[((key % 7) + 7) % 7]

// ── Typography ────────────────────────────────────────────────────────────────

private val LongboxTypography = Typography(
    // Display and headline: heavier weight for the retro-modern feel.
    displayLarge  = TextStyle(fontWeight = FontWeight.Black,  fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium= TextStyle(fontWeight = FontWeight.Bold,   fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

// ── Theme entry point ─────────────────────────────────────────────────────────

/** User-controlled theme override; SYSTEM defers to [isSystemInDarkTheme]. */
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

@Composable
fun LongboxTheme(isDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark) DarkColors else LightColors,
        typography = LongboxTypography,
        content = content,
    )
}
