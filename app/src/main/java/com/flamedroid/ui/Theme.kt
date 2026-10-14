package com.flamedroid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ember = Color(0xFFFF7A1A)
private val Gold = Color(0xFFFFD36B)

private val Scheme = darkColorScheme(
    primary = Ember,
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = Color(0xFF4A2208),
    onPrimaryContainer = Gold,
    secondary = Gold,
    onSecondary = Color(0xFF1A1200),
    secondaryContainer = Color(0xFF2E2A1E),
    onSecondaryContainer = Gold,
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFECE7E1),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFECE7E1),
    surfaceVariant = Color(0xFF1E1E26),
    onSurfaceVariant = Color(0xFFB9B3AC),
    surfaceContainer = Color(0xFF17171E),
    surfaceContainerHigh = Color(0xFF1E1E26),
    surfaceContainerHighest = Color(0xFF26262F),
    outline = Color(0xFF4A4A55),
    outlineVariant = Color(0xFF2E2E38),
)

@Composable
fun FlameTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography(), content = content)
}
