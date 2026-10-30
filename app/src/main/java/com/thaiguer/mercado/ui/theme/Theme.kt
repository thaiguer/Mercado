
package com.thaiguer.mercado.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    secondary = Color(0xFFB0BEC5),
    tertiary = Color(0xFFFFCC80),
    background = Color(0xFF121C16),
    surface = Color(0xFF121C16),
    onPrimary = Color(0xFF003912),
    onSecondary = Color(0xFF26332B),
    onTertiary = Color(0xFF422900),
    onBackground = Color(0xFFE1E9E1),
    onSurface = Color(0xFFE1E9E1)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF087F3C),
    secondary = Color(0xFF455A50),
    tertiary = Color(0xFFEF8C24),
    background = Color(0xFFF7FAF7),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color(0xFF252015),
    onBackground = Color(0xFF191F1A),
    onSurface = Color(0xFF191F1A)
)

@Composable
fun MercadoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
