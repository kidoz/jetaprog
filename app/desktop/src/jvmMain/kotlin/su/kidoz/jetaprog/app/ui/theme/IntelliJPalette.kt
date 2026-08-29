package su.kidoz.jetaprog.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The active IDE color palette.
 *
 * Implemented by the dark [IntelliJColors] and light [IntelliJLightColors]
 * token objects (full key parity). Composables must read colors through
 * [LocalIntelliJColors] instead of referencing a palette object directly so
 * both themes render correctly.
 */
public interface IntelliJPalette {
    /** Whether this palette is the dark one; selects the matching syntax theme. */
    public val isDark: Boolean

    public val background: Color
    public val backgroundDarker: Color
    public val backgroundLighter: Color
    public val surface: Color
    public val surfaceElevated: Color
    public val surfaceContainer: Color
    public val surfaceHover: Color
    public val toolWindowBackground: Color
    public val toolWindowHeader: Color
    public val toolWindowBorder: Color
    public val textPrimary: Color
    public val textSecondary: Color
    public val textMuted: Color
    public val textDisabled: Color
    public val textLink: Color
    public val textInverse: Color
    public val accent: Color
    public val accentHover: Color
    public val accentPressed: Color
    public val accentMuted: Color
    public val accentSubtle: Color
    public val success: Color
    public val successMuted: Color
    public val warning: Color
    public val warningMuted: Color
    public val error: Color
    public val errorMuted: Color
    public val info: Color
    public val infoMuted: Color
    public val editorBackground: Color
    public val editorGutter: Color
    public val editorLineHighlight: Color
    public val editorSelection: Color
    public val editorCaretRow: Color
    public val editorCurrentLine: Color
    public val editorCaret: Color
    public val editorSelectionActive: Color
    public val editorIdentifier: Color
    public val tabBackground: Color
    public val tabBackgroundSelected: Color
    public val tabBackgroundHover: Color
    public val tabBorder: Color
    public val tabUnderline: Color
    public val activityBarBackground: Color
    public val activityBarForeground: Color
    public val activityBarForegroundActive: Color
    public val activityBarIndicator: Color
    public val activityBarHover: Color
    public val treeBackground: Color
    public val treeSelectionBackground: Color
    public val treeSelectionInactive: Color
    public val treeForeground: Color
    public val treeHoverBackground: Color
    public val treeForegroundIgnored: Color
    public val treeIndentGuide: Color
    public val treeSelectionAccent: Color
    public val statusBarBackground: Color
    public val statusBarForeground: Color
    public val statusBarHover: Color
    public val statusBarDivider: Color
    public val buttonBackground: Color
    public val buttonBackgroundHover: Color
    public val buttonBackgroundPressed: Color
    public val buttonForeground: Color
    public val buttonPrimaryBackground: Color
    public val buttonPrimaryBackgroundHover: Color
    public val buttonPrimaryForeground: Color
    public val buttonDangerBackground: Color
    public val buttonDangerBackgroundHover: Color
    public val buttonDangerForeground: Color
    public val inputBackground: Color
    public val inputBackgroundHover: Color
    public val inputBorder: Color
    public val inputBorderHover: Color
    public val inputBorderFocused: Color
    public val inputPlaceholder: Color
    public val scrollbarThumb: Color
    public val scrollbarThumbHover: Color
    public val scrollbarTrack: Color
    public val divider: Color
    public val border: Color
    public val borderSubtle: Color
    public val terminalBackground: Color
    public val terminalHeader: Color
    public val terminalInputBackground: Color
    public val terminalForeground: Color
    public val terminalCursor: Color
    public val terminalSelectionBackground: Color
    public val terminalGreen: Color
    public val terminalRed: Color
    public val terminalYellow: Color
    public val terminalBlue: Color
    public val terminalMagenta: Color
    public val terminalCyan: Color
    public val gutterBackground: Color
    public val lineNumberForeground: Color
    public val lineNumberForegroundActive: Color
    public val breadcrumbsBackground: Color
    public val breadcrumbsForeground: Color
    public val breadcrumbsForegroundHover: Color
    public val breadcrumbsFileForeground: Color
    public val breadcrumbsBackgroundHover: Color
    public val breadcrumbsSeparator: Color
    public val iconDefault: Color
    public val iconFolder: Color
    public val iconFile: Color
    public val iconKotlin: Color
    public val iconJava: Color
    public val iconRust: Color
    public val iconCpp: Color
    public val iconVala: Color
    public val iconPython: Color
    public val iconGit: Color
    public val lineNumberText: Color
    public val popupBackground: Color
    public val popupBorder: Color
    public val popupShadow: Color
    public val menuItemHover: Color
    public val dialogBackground: Color
    public val dialogOverlay: Color
    public val focusRing: Color
    public val selectionBackground: Color
    public val selectionInactive: Color
    public val editorIndentGuide: Color
    public val editorIndentGuideActive: Color
    public val diagnosticErrorStripe: Color
    public val diagnosticWarningStripe: Color
    public val diagnosticInfoStripe: Color
    public val diagnosticHintStripe: Color
    public val notificationBackground: Color
    public val notificationBorder: Color
    public val notificationInfoStripe: Color
    public val notificationSuccessStripe: Color
    public val notificationWarningStripe: Color
    public val notificationErrorStripe: Color
    public val welcomeRecentRowHover: Color
    public val brandGradientStart: Color
    public val brandGradientEnd: Color
    public val toolCardBackground: Color
    public val agentCardBorder: Color
    public val codeBlockBackground: Color
    public val agentEffortText: Color
    public val agentPillBorder: Color
    public val agentPillText: Color
    public val diffAcceptBackground: Color
    public val diffAddedBackground: Color
    public val diffRemovedBackground: Color
    public val diffAddedText: Color
    public val diffRemovedText: Color
    public val diffAddedGutter: Color
    public val approvalBorder: Color
    public val approvalText: Color
    public val agentStopBorder: Color
    public val agentStopText: Color
    public val breakpointRed: Color
    public val executionLineBackground: Color
    public val inlineValueText: Color
    public val debugPausedText: Color
    public val debugRunningText: Color
    public val debugVarName: Color
    public val debugVarString: Color
    public val debugVarNumber: Color
    public val debugVarType: Color
    public val windowCloseButton: Color
    public val windowMinimizeButton: Color
    public val windowZoomButton: Color

    // File type badges (shared by tree icons and editor tab badges)
    public val fileIconMarkup: Color
    public val fileIconData: Color
    public val fileIconMarkdown: Color
    public val fileIconGradle: Color
    public val fileIconToml: Color
    public val fileIconJavascript: Color
    public val fileIconTypescript: Color
}

/** The composition-local holding the active [IntelliJPalette]. Defaults to dark. */
public val LocalIntelliJColors: androidx.compose.runtime.ProvidableCompositionLocal<IntelliJPalette> =
    androidx.compose.runtime.staticCompositionLocalOf { IntelliJColors }
