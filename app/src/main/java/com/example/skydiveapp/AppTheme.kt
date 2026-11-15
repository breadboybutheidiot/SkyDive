package com.example.skydiveapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightColors = lightColorScheme(
    primary = Color(0xFF454A65),
    onPrimary = Color.White,
    background = Color(0xFFF9F7FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFE2E2EC),
    surfaceVariant = Color(0xFFF0EFF6),
    onSurface = Color(0xFF1C1B20),
    onSurfaceVariant = Color(0xFF56545F),
    secondaryContainer = Color(0xFFDFE4FA),
    onSecondaryContainer = Color(0xFF1C1B20),
    outlineVariant = Color(0xFFD3D3DE),
    secondary = Color(0xFF777D9B)
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFC5C9EC),
    onPrimary = Color(0xFF262B40),
    background = Color(0xFF17171F),
    onBackground = Color(0xFFE8E6F0),
    surface = Color(0xFF2D2D39),
    surfaceVariant = Color(0xFF22222D),
    onSurface = Color(0xFFE8E6F0),
    onSurfaceVariant = Color(0xFFC9C7D4),
    secondaryContainer = Color(0xFF41455D),
    onSecondaryContainer = Color(0xFFE8E6F0),
    outlineVariant = Color(0xFF53535F),
    secondary = Color(0xFFB6BDDD)
)

@Composable
internal fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkColors else lightColors, content = content)
}
