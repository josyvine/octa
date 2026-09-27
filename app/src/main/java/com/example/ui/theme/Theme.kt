package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = OctaCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF004E59),
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = OctaCobalt,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1F2D69),
    onSecondaryContainer = Color(0xFFDCE1FF),
    tertiary = OctaEmerald,
    onTertiary = Color(0xFF003919),
    tertiaryContainer = Color(0xFF005227),
    onTertiaryContainer = Color(0xFFB4FFD2),
    error = OctaCoralRed,
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFFFDAD6),
    background = ObsidianBackground,
    onBackground = Color(0xFFE6EDF7),
    surface = ObsidianSurface,
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFFA7B4C8),
    outline = ObsidianOutline
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB3EBF2),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE1FF),
    onSecondaryContainer = Color(0xFF001551),
    tertiary = Color(0xFF006D36),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF111827),
    surface = LightSurface,
    onSurface = Color(0xFF111827),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFCBD5E1)
)

val OctaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OctaStreamTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = OctaShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    OctaStreamTheme(darkTheme = darkTheme, content = content)
}
