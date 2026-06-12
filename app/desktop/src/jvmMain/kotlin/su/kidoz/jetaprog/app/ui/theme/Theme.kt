package su.kidoz.jetaprog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF90CAF9),
        secondary = Color(0xFF80CBC4),
        tertiary = Color(0xFFCE93D8),
        background = Color(0xFF1E1E1E),
        surface = Color(0xFF252526),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onTertiary = Color.Black,
        onBackground = Color(0xFFD4D4D4),
        onSurface = Color(0xFFD4D4D4),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF1976D2),
        secondary = Color(0xFF00897B),
        tertiary = Color(0xFF7B1FA2),
        background = Color(0xFFF5F5F5),
        surface = Color.White,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = Color(0xFF1E1E1E),
        onSurface = Color(0xFF1E1E1E),
    )

/**
 * JetaProg IDE theme.
 */
@Composable
public fun JetaProgTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
