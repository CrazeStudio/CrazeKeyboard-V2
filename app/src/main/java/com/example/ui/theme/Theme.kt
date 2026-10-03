package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4A7C59),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2C3E35),
    onPrimaryContainer = Color(0xFFD0E0CC),
    secondary = Color(0xFF9C6B30),
    background = Color(0xFF1A1A18),
    surface = Color(0xFF22221F),
    surfaceVariant = Color(0xFF2C2C28),
    onBackground = Color(0xFFEBEBE6),
    onSurface = Color(0xFFEBEBE6),
    outline = Color(0xFF3F3F38)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4A7C59),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E0CC),
    onPrimaryContainer = Color(0xFF1E3625),
    secondary = Color(0xFF9C6B30),
    background = Color(0xFFF9F9F7),
    surface = Color(0xFFF1F1EC),
    surfaceVariant = Color(0xFFE5E5DE),
    onBackground = Color(0xFF2A2A28),
    onSurface = Color(0xFF2A2A28),
    outline = Color(0xFFCACACA)
)

@Composable
fun CrazeKeyboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

