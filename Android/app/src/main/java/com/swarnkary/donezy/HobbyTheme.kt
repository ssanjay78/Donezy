package com.swarnkary.donezy

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Color palette ────────────────────────────────────────────────────────────

private val GreenPrimary          = Color(0xFF1A6B48)
private val GreenOnPrimary        = Color(0xFFFFFFFF)
private val GreenPrimaryContainer = Color(0xFFB4F1D0)
private val GreenOnPrimaryContainer = Color(0xFF002113)

private val AmberSecondary          = Color(0xFF7B4B2A)
private val AmberOnSecondary        = Color(0xFFFFFFFF)
private val AmberSecondaryContainer = Color(0xFFFFDBCB)
private val AmberOnSecondaryContainer = Color(0xFF2E1200)

private val BlueThird             = Color(0xFF1B61A4)
private val BlueOnThird           = Color(0xFFFFFFFF)
private val BlueThirdContainer    = Color(0xFFD7E3FF)
private val BlueOnThirdContainer  = Color(0xFF001C3B)

private val LightBackground       = Color(0xFFF5F7F2)
private val LightSurface          = Color(0xFFFFFFFF)
private val LightSurfaceVariant   = Color(0xFFDCE5DB)
private val LightOnSurface        = Color(0xFF191C19)
private val LightOnSurfaceVariant = Color(0xFF404943)

// Dark palette
private val GreenPrimaryDark         = Color(0xFF6ECFA3)
private val GreenOnPrimaryDark       = Color(0xFF003823)
private val GreenPrimaryContainerDark = Color(0xFF005235)
private val GreenOnPrimaryContainerDark = Color(0xFFB4F1D0)

private val AmberSecondaryDark         = Color(0xFFFFB68E)
private val AmberOnSecondaryDark       = Color(0xFF4A2000)
private val AmberSecondaryContainerDark = Color(0xFF5F3210)
private val AmberOnSecondaryContainerDark = Color(0xFFFFDBCB)

private val BlueTertiaryDark          = Color(0xFFB0C8FF)
private val BlueOnTertiaryDark        = Color(0xFF002E60)
private val BlueTertiaryContainerDark = Color(0xFF00448A)
private val BlueOnTertiaryContainerDark = Color(0xFFD7E3FF)

private val DarkBackground       = Color(0xFF0E1512)
private val DarkSurface          = Color(0xFF161D1A)
private val DarkSurfaceVariant   = Color(0xFF3A413D)
private val DarkOnSurface        = Color(0xFFDFE4DC)
private val DarkOnSurfaceVariant = Color(0xFFBFC9C2)

private val LightColors = lightColorScheme(
    primary                = GreenPrimary,
    onPrimary              = GreenOnPrimary,
    primaryContainer       = GreenPrimaryContainer,
    onPrimaryContainer     = GreenOnPrimaryContainer,
    secondary              = AmberSecondary,
    onSecondary            = AmberOnSecondary,
    secondaryContainer     = AmberSecondaryContainer,
    onSecondaryContainer   = AmberOnSecondaryContainer,
    tertiary               = BlueThird,
    onTertiary             = BlueOnThird,
    tertiaryContainer      = BlueThirdContainer,
    onTertiaryContainer    = BlueOnThirdContainer,
    background             = LightBackground,
    onBackground           = LightOnSurface,
    surface                = LightSurface,
    onSurface              = LightOnSurface,
    surfaceVariant         = LightSurfaceVariant,
    onSurfaceVariant       = LightOnSurfaceVariant,
    error                  = Color(0xFFB3261E),
    onError                = Color(0xFFFFFFFF),
    errorContainer         = Color(0xFFF9DEDC),
    onErrorContainer       = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary                = GreenPrimaryDark,
    onPrimary              = GreenOnPrimaryDark,
    primaryContainer       = GreenPrimaryContainerDark,
    onPrimaryContainer     = GreenOnPrimaryContainerDark,
    secondary              = AmberSecondaryDark,
    onSecondary            = AmberOnSecondaryDark,
    secondaryContainer     = AmberSecondaryContainerDark,
    onSecondaryContainer   = AmberOnSecondaryContainerDark,
    tertiary               = BlueTertiaryDark,
    onTertiary             = BlueOnTertiaryDark,
    tertiaryContainer      = BlueTertiaryContainerDark,
    onTertiaryContainer    = BlueOnTertiaryContainerDark,
    background             = DarkBackground,
    onBackground           = DarkOnSurface,
    surface                = DarkSurface,
    onSurface              = DarkOnSurface,
    surfaceVariant         = DarkSurfaceVariant,
    onSurfaceVariant       = DarkOnSurfaceVariant,
    error                  = Color(0xFFF2B8B5),
    onError                = Color(0xFF601410),
    errorContainer         = Color(0xFF8C1D18),
    onErrorContainer       = Color(0xFFF9DEDC),
)

// ─── Typography ───────────────────────────────────────────────────────────────

private val HobbyTypography = Typography(
    displayLarge  = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium= TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge    = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium    = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall     = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

// ─── Theme ────────────────────────────────────────────────────────────────────

@Composable
fun HobbyLogTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light  -> false
        ThemeMode.Dark   -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HobbyTypography,
        content = content
    )
}
