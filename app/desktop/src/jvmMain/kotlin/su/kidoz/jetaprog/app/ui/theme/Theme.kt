package su.kidoz.jetaprog.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Material slots are derived from the design tokens so any Material component that
// falls back to the colorScheme (menus, dialogs, tonal surfaces) stays on-palette
// instead of picking up the purple-tinted M3 baseline.
private val DarkColorScheme =
    darkColorScheme(
        primary = IntelliJColors.accent,
        onPrimary = IntelliJColors.buttonPrimaryForeground,
        primaryContainer = IntelliJColors.accentSubtle,
        onPrimaryContainer = IntelliJColors.textPrimary,
        secondary = IntelliJColors.accentMuted,
        onSecondary = IntelliJColors.buttonPrimaryForeground,
        tertiary = IntelliJColors.brandGradientEnd,
        onTertiary = IntelliJColors.buttonPrimaryForeground,
        background = IntelliJColors.background,
        onBackground = IntelliJColors.textPrimary,
        surface = IntelliJColors.surface,
        onSurface = IntelliJColors.textPrimary,
        surfaceVariant = IntelliJColors.surfaceElevated,
        onSurfaceVariant = IntelliJColors.textSecondary,
        surfaceContainerLowest = IntelliJColors.backgroundDarker,
        surfaceContainerLow = IntelliJColors.surface,
        surfaceContainer = IntelliJColors.surfaceElevated,
        surfaceContainerHigh = IntelliJColors.surfaceContainer,
        surfaceContainerHighest = IntelliJColors.surfaceHover,
        surfaceTint = Color.Transparent,
        outline = IntelliJColors.border,
        outlineVariant = IntelliJColors.divider,
        error = IntelliJColors.error,
        onError = IntelliJColors.buttonDangerForeground,
        errorContainer = IntelliJColors.errorMuted,
        onErrorContainer = IntelliJColors.error,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = IntelliJLightColors.accent,
        onPrimary = IntelliJLightColors.buttonPrimaryForeground,
        primaryContainer = IntelliJLightColors.accentSubtle,
        onPrimaryContainer = IntelliJLightColors.textPrimary,
        secondary = IntelliJLightColors.accentMuted,
        onSecondary = IntelliJLightColors.buttonPrimaryForeground,
        tertiary = IntelliJLightColors.brandGradientEnd,
        onTertiary = IntelliJLightColors.buttonPrimaryForeground,
        background = IntelliJLightColors.background,
        onBackground = IntelliJLightColors.textPrimary,
        surface = IntelliJLightColors.surface,
        onSurface = IntelliJLightColors.textPrimary,
        surfaceVariant = IntelliJLightColors.surfaceElevated,
        onSurfaceVariant = IntelliJLightColors.textSecondary,
        surfaceContainerLowest = IntelliJLightColors.backgroundDarker,
        surfaceContainerLow = IntelliJLightColors.surface,
        surfaceContainer = IntelliJLightColors.surfaceElevated,
        surfaceContainerHigh = IntelliJLightColors.surfaceContainer,
        surfaceContainerHighest = IntelliJLightColors.surfaceHover,
        surfaceTint = Color.Transparent,
        outline = IntelliJLightColors.border,
        outlineVariant = IntelliJLightColors.divider,
        error = IntelliJLightColors.error,
        onError = IntelliJLightColors.buttonDangerForeground,
        errorContainer = IntelliJLightColors.errorMuted,
        onErrorContainer = IntelliJLightColors.error,
    )

// JetBrains Mono is the contract face for the whole IDE, not only the editor.
// Body/label roles carry the 10-13sp UI scale; mono needs no letter tracking.
@Suppress("MagicNumber")
private val JetaProgTypography: Typography by lazy {
    val mono = JetaProgFonts.jetBrainsMono
    val base = Typography()
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = mono),
        displayMedium = base.displayMedium.copy(fontFamily = mono),
        displaySmall = base.displaySmall.copy(fontFamily = mono),
        headlineLarge = base.headlineLarge.copy(fontFamily = mono),
        headlineMedium = base.headlineMedium.copy(fontFamily = mono),
        headlineSmall = base.headlineSmall.copy(fontFamily = mono),
        titleLarge = base.titleLarge.copy(fontFamily = mono, fontSize = 16.sp, lineHeight = 24.sp),
        titleMedium =
            base.titleMedium.copy(fontFamily = mono, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
        titleSmall =
            base.titleSmall.copy(fontFamily = mono, fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodyLarge =
            base.bodyLarge.copy(fontFamily = mono, fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodyMedium =
            base.bodyMedium.copy(fontFamily = mono, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
        bodySmall =
            base.bodySmall.copy(fontFamily = mono, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
        labelLarge =
            base.labelLarge.copy(fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
        labelMedium =
            base.labelMedium.copy(fontFamily = mono, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
        labelSmall =
            base.labelSmall.copy(fontFamily = mono, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.sp),
    )
}

/**
 * JetaProg IDE theme.
 *
 * Supplies a Material color scheme derived from the design tokens and sets
 * JetBrains Mono as the default typeface, so `Text` calls without an explicit
 * `fontFamily` still render on-contract.
 */
@Composable
public fun JetaProgTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JetaProgTypography,
        content = content,
    )
}
