package su.kidoz.jetaprog.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * JetBrains Mono font family for code editing.
 *
 * JetBrains Mono is a typeface designed specifically for developers.
 * Features:
 * - Increased height for better readability
 * - Clear distinction between similar characters (0/O, 1/l/I)
 * - Curated ligatures for code readability
 * - Optimized for reduced eye strain
 *
 * License: SIL Open Font License 1.1
 * Source: https://www.jetbrains.com/mono/
 */
public object JetaProgFonts {
    /**
     * JetBrains Mono font family for code editing.
     */
    public val jetBrainsMono: FontFamily by lazy {
        FontFamily(
            Font(
                resource = "fonts/JetBrainsMono-Light.ttf",
                weight = FontWeight.Light,
                style = FontStyle.Normal,
            ),
            Font(
                resource = "fonts/JetBrainsMono-Regular.ttf",
                weight = FontWeight.Normal,
                style = FontStyle.Normal,
            ),
            Font(
                resource = "fonts/JetBrainsMono-Medium.ttf",
                weight = FontWeight.Medium,
                style = FontStyle.Normal,
            ),
            Font(
                resource = "fonts/JetBrainsMono-Bold.ttf",
                weight = FontWeight.Bold,
                style = FontStyle.Normal,
            ),
            Font(
                resource = "fonts/JetBrainsMono-Italic.ttf",
                weight = FontWeight.Normal,
                style = FontStyle.Italic,
            ),
            Font(
                resource = "fonts/JetBrainsMono-BoldItalic.ttf",
                weight = FontWeight.Bold,
                style = FontStyle.Italic,
            ),
        )
    }

    /**
     * Default code font - JetBrains Mono.
     */
    public val codeFont: FontFamily
        get() = jetBrainsMono
}

/**
 * Provides the code font family for composables.
 */
@Composable
public fun codeFont(): FontFamily = JetaProgFonts.codeFont
