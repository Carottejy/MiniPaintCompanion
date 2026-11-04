package com.carottejy.minipaintcompanion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette « établi de peintre » : bois sombre, laiton, rouge vermillon.
val Brass = Color(0xFFD9A441)
val Vermilion = Color(0xFFC8553D)
val Ink = Color(0xFF17140F)
val Bench = Color(0xFF211C16)
val BenchHigh = Color(0xFF2E261C)
val Parchment = Color(0xFFEDE3D1)
val Dust = Color(0xFFA89C88)

private val scheme = darkColorScheme(
    primary = Brass,
    onPrimary = Ink,
    primaryContainer = Color(0xFF4A3814),
    onPrimaryContainer = Color(0xFFF6DFA8),
    secondary = Vermilion,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF5A2418),
    onSecondaryContainer = Color(0xFFFFD9CF),
    tertiary = Color(0xFF7FB3A6),
    background = Ink,
    onBackground = Parchment,
    surface = Ink,
    onSurface = Parchment,
    surfaceVariant = BenchHigh,
    onSurfaceVariant = Dust,
    surfaceContainerLowest = Color(0xFF120F0B),
    surfaceContainerLow = Color(0xFF1C1813),
    surfaceContainer = Bench,
    surfaceContainerHigh = BenchHigh,
    surfaceContainerHighest = Color(0xFF3A3024),
    outline = Color(0xFF5E5242),
    outlineVariant = Color(0xFF3E3529),
)

private val serif = FontFamily.Serif
private val base = Typography()
private val typography = base.copy(
    headlineLarge = base.headlineLarge.copy(fontFamily = serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = serif, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = serif, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun MiniPaintTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
