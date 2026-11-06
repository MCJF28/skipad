package com.dsh.skipad.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF1F6FEB)
private val BrandDark = Color(0xFF8AB4F8)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0A2E6B),
    secondaryContainer = Color(0xFFE8EEF9),
    onSecondaryContainer = Color(0xFF1B2B44),
    background = Color(0xFFF7F8FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF1F7),
    error = Color(0xFFD93025),
    errorContainer = Color(0xFFFFE4E1),
    onErrorContainer = Color(0xFF5C0F0A)
)

private val DarkColors = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF06203F),
    primaryContainer = Color(0xFF1B3A5C),
    onPrimaryContainer = Color(0xFFD6E5FF),
    secondaryContainer = Color(0xFF24313F),
    onSecondaryContainer = Color(0xFFDCE6F2),
    background = Color(0xFF101317),
    surface = Color(0xFF171B21),
    surfaceVariant = Color(0xFF232A33),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF4A1512),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun SkipAdTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
