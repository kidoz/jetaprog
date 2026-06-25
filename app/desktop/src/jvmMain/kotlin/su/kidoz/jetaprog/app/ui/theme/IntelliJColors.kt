package su.kidoz.jetaprog.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Modern flat dark theme color palette.
 * Inspired by IntelliJ IDEA Darcula but with improved contrast and modern aesthetics.
 */
public object IntelliJColors {
    // ============================================================
    // BACKGROUND COLORS (warmer, less harsh)
    // ============================================================
    public val background: Color = Color(0xFF1A1B1E)
    public val backgroundDarker: Color = Color(0xFF141517)
    public val backgroundLighter: Color = Color(0xFF252629)

    // ============================================================
    // SURFACE COLORS (subtle differentiation)
    // ============================================================
    public val surface: Color = Color(0xFF1F2023)
    public val surfaceElevated: Color = Color(0xFF232427)
    public val surfaceContainer: Color = Color(0xFF282A2E)
    public val surfaceHover: Color = Color(0xFF2D2F33)

    // Panel colors (aliased for compatibility)
    public val toolWindowBackground: Color = surface
    public val toolWindowHeader: Color = surfaceElevated
    public val toolWindowBorder: Color = Color(0xFF2A2B2F)

    // ============================================================
    // TEXT COLORS (improved contrast) - must be defined early
    // ============================================================
    public val textPrimary: Color = Color(0xFFD4D4D4)
    public val textSecondary: Color = Color(0xFF9D9D9D)
    public val textMuted: Color = Color(0xFF6B6B6B)
    public val textDisabled: Color = Color(0xFF505050)
    public val textLink: Color = Color(0xFF6BB3F8)
    public val textInverse: Color = Color(0xFF1A1B1E)

    // ============================================================
    // ACCENT COLORS (more vibrant) - must be defined early
    // ============================================================
    public val accent: Color = Color(0xFF5B9BD5)
    public val accentHover: Color = Color(0xFF6BAADF)
    public val accentPressed: Color = Color(0xFF4B8BC5)
    public val accentMuted: Color = Color(0xFF3D5A80)
    public val accentSubtle: Color = Color(0xFF264F78)

    // Semantic colors
    public val success: Color = Color(0xFF4EC969)
    public val successMuted: Color = Color(0xFF2D4A35)
    public val warning: Color = Color(0xFFDBA800)
    public val warningMuted: Color = Color(0xFF4A4020)
    public val error: Color = Color(0xFFF85149)
    public val errorMuted: Color = Color(0xFF4A2A2A)
    public val info: Color = Color(0xFF58A6FF)
    public val infoMuted: Color = Color(0xFF264F78)

    // ============================================================
    // EDITOR COLORS
    // ============================================================
    public val editorBackground: Color = background
    public val editorGutter: Color = Color(0xFF1E1F22)
    public val editorLineHighlight: Color = Color(0xFF1E2126)
    public val editorSelection: Color = Color(0xFF264F78)
    public val editorCaretRow: Color = Color(0xFF1E2126)

    /** Caret-line background highlight (brighter than [editorCaretRow]). */
    public val editorCurrentLine: Color = Color(0xFF20242B)

    /** Blinking caret bar color. */
    public val editorCaret: Color = Color(0xFFAEAFAD)

    /** Active text-selection background inside the editor. */
    public val editorSelectionActive: Color = Color(0xFF2D4A6B)

    /** Default identifier/body text — slightly brighter than [textPrimary] for code. */
    public val editorIdentifier: Color = Color(0xFFD7DBE0)

    // ============================================================
    // TAB COLORS (modern flat style)
    // ============================================================
    public val tabBackground: Color = Color.Transparent
    public val tabBackgroundSelected: Color = Color(0xFF2D2F33)
    public val tabBackgroundHover: Color = Color(0xFF252629)
    public val tabBorder: Color = Color.Transparent
    public val tabUnderline: Color = accent

    // ============================================================
    // ACTIVITY BAR (left sidebar icons)
    // ============================================================
    public val activityBarBackground: Color = backgroundDarker
    public val activityBarForeground: Color = Color(0xFF8B8D91)
    public val activityBarForegroundActive: Color = Color(0xFFFFFFFF)
    public val activityBarIndicator: Color = accent
    public val activityBarHover: Color = Color(0xFF252629)

    // ============================================================
    // SIDEBAR/TREE
    // ============================================================
    public val treeBackground: Color = surface
    public val treeSelectionBackground: Color = Color(0xFF2D4F6E)
    public val treeSelectionInactive: Color = Color(0xFF2A2D30)
    public val treeForeground: Color = textPrimary
    public val treeHoverBackground: Color = Color(0xFF252629)

    /** Vertical indent-guide line in the project tree (one per depth level). */
    public val treeIndentGuide: Color = Color(0xFF2A2C30)

    /** Left accent bar drawn on the selected tree row. */
    public val treeSelectionAccent: Color = accent

    // ============================================================
    // STATUS BAR
    // ============================================================
    public val statusBarBackground: Color = backgroundDarker
    public val statusBarForeground: Color = textSecondary
    public val statusBarHover: Color = Color(0xFF252629)
    public val statusBarDivider: Color = Color(0xFF2A2B2F)

    // ============================================================
    // BUTTON COLORS
    // ============================================================
    public val buttonBackground: Color = Color(0xFF2D2F33)
    public val buttonBackgroundHover: Color = Color(0xFF363840)
    public val buttonBackgroundPressed: Color = Color(0xFF252629)
    public val buttonForeground: Color = textPrimary

    public val buttonPrimaryBackground: Color = accent
    public val buttonPrimaryBackgroundHover: Color = accentHover
    public val buttonPrimaryForeground: Color = Color(0xFFFFFFFF)

    public val buttonDangerBackground: Color = Color(0xFF8B3038)
    public val buttonDangerBackgroundHover: Color = Color(0xFFA03840)
    public val buttonDangerForeground: Color = Color(0xFFFFFFFF)

    // ============================================================
    // INPUT/TEXTFIELD COLORS
    // ============================================================
    public val inputBackground: Color = Color(0xFF1E1F22)
    public val inputBackgroundHover: Color = Color(0xFF232427)
    public val inputBorder: Color = Color(0xFF3D3F42)
    public val inputBorderHover: Color = Color(0xFF4D4F52)
    public val inputBorderFocused: Color = accent
    public val inputPlaceholder: Color = textMuted

    // ============================================================
    // SCROLLBAR COLORS (thin, modern)
    // ============================================================
    public val scrollbarThumb: Color = Color(0xFF4A4C50)
    public val scrollbarThumbHover: Color = Color(0xFF5A5C60)
    public val scrollbarTrack: Color = Color.Transparent

    // ============================================================
    // DIVIDER/BORDER
    // ============================================================
    public val divider: Color = Color(0xFF2A2B2F)
    public val border: Color = Color(0xFF3D3F42)
    public val borderSubtle: Color = Color(0xFF232427)

    // ============================================================
    // TERMINAL COLORS
    // ============================================================
    public val terminalBackground: Color = background
    public val terminalHeader: Color = surfaceElevated
    public val terminalInputBackground: Color = Color(0xFF2D2D30)
    public val terminalForeground: Color = textPrimary
    public val terminalCursor: Color = accent
    public val terminalSelectionBackground: Color = editorSelection
    public val terminalGreen: Color = Color(0xFF4EC9B0)
    public val terminalRed: Color = Color(0xFFE74C3C)
    public val terminalYellow: Color = Color(0xFFDCDCAA)
    public val terminalBlue: Color = Color(0xFF569CD6)
    public val terminalMagenta: Color = Color(0xFFC586C0)
    public val terminalCyan: Color = Color(0xFF4EC9B0)

    // ============================================================
    // GUTTER COLORS
    // ============================================================
    public val gutterBackground: Color = editorGutter
    public val lineNumberForeground: Color = Color(0xFF5A5D63)
    public val lineNumberForegroundActive: Color = Color(0xFFC9CDD2)

    // ============================================================
    // BREADCRUMBS
    // ============================================================
    public val breadcrumbsBackground: Color = Color.Transparent
    public val breadcrumbsForeground: Color = textSecondary
    public val breadcrumbsForegroundHover: Color = textPrimary
    public val breadcrumbsFileForeground: Color = textPrimary
    public val breadcrumbsBackgroundHover: Color = surfaceHover
    public val breadcrumbsSeparator: Color = textMuted

    // ============================================================
    // ICONS
    // ============================================================
    public val iconDefault: Color = Color(0xFF9D9FA3)
    public val iconFolder: Color = Color(0xFFD4A656)
    public val iconFile: Color = Color(0xFF8AB4F8)
    public val iconKotlin: Color = Color(0xFF7F52FF)
    public val iconJava: Color = Color(0xFFE37933)
    public val iconRust: Color = Color(0xFFDEA584)
    public val iconCpp: Color = Color(0xFF5C8DBC)
    public val iconVala: Color = Color(0xFF7239B3)
    public val iconPython: Color = Color(0xFF3776AB)

    // Line number text color (alias for navigation components)
    public val lineNumberText: Color = lineNumberForeground

    // ============================================================
    // DROPDOWN/POPUP COLORS
    // ============================================================
    public val popupBackground: Color = surfaceContainer
    public val popupBorder: Color = Color(0xFF3D3F42)
    public val popupShadow: Color = Color(0x40000000)
    public val menuItemHover: Color = Color(0xFF2D4F6E)

    // ============================================================
    // DIALOG COLORS
    // ============================================================
    public val dialogBackground: Color = surfaceElevated
    public val dialogOverlay: Color = Color(0xB3000000)

    // ============================================================
    // FOCUS/SELECTION
    // ============================================================
    public val focusRing: Color = accent.copy(alpha = 0.5f)
    public val selectionBackground: Color = accentSubtle
    public val selectionInactive: Color = Color(0xFF2A2D30)

    // ============================================================
    // EDITOR EXTRAS (indent guides, diagnostics)
    // ============================================================
    public val editorIndentGuide: Color = Color(0xFF2A2B2F)
    public val editorIndentGuideActive: Color = Color(0xFF4A4C50)
    public val diagnosticErrorStripe: Color = error
    public val diagnosticWarningStripe: Color = warning
    public val diagnosticInfoStripe: Color = info
    public val diagnosticHintStripe: Color = textMuted

    // ============================================================
    // NOTIFICATIONS (toast / banner)
    // ============================================================
    public val notificationBackground: Color = surfaceContainer
    public val notificationBorder: Color = border
    public val notificationInfoStripe: Color = info
    public val notificationSuccessStripe: Color = success
    public val notificationWarningStripe: Color = warning
    public val notificationErrorStripe: Color = error

    // ============================================================
    // WELCOME HUB
    // ============================================================

    /** Background of a hovered / first recent-project row. */
    public val welcomeRecentRowHover: Color = Color(0xFF232831)

    /** Brand "J" tile gradient — start color. */
    public val brandGradientStart: Color = Color(0xFF5B9BD5)

    /** Brand "J" tile gradient — end color. */
    public val brandGradientEnd: Color = Color(0xFF7F52FF)
}

/**
 * Spacing constants for consistent layout.
 */
public object Spacing {
    public val xxs: Int = 2
    public val xs: Int = 4
    public val sm: Int = 8
    public val md: Int = 12
    public val lg: Int = 16
    public val xl: Int = 24
    public val xxl: Int = 32
}

/**
 * Common dimensions for UI components. All values are dp.
 */
@Suppress("MagicNumber")
public object Dimensions {
    // Shell
    public val activityBarWidth: Int = 48
    public val statusBarHeight: Int = 24
    public val menuBarHeight: Int = 32
    public val mainToolbarHeight: Int = 32

    // Welcome hub
    public val welcomeRailWidth: Int = 230
    public val welcomeRailItemHeight: Int = 34
    public val welcomeRecentRowHeight: Int = 60

    // Tool windows
    public val toolWindowHeaderHeight: Int = 24
    public val toolWindowDefaultWidth: Int = 280
    public val toolWindowMinWidth: Int = 180
    public val toolWindowMaxWidth: Int = 640
    public val toolWindowDefaultBottomHeight: Int = 240
    public val toolWindowMinBottomHeight: Int = 80

    // Editor area
    public val tabHeight: Int = 32
    public val panelHeaderHeight: Int = 32
    public val breadcrumbsHeight: Int = 24
    public val lineHeightCode: Int = 21

    // Controls
    public val buttonHeight: Int = 32
    public val inputHeight: Int = 36
    public val treeNodeHeight: Int = 28
    public val toolbarIcon: Int = 24

    // Icons
    public val iconXs: Int = 12
    public val iconSm: Int = 14
    public val iconMd: Int = 16
    public val iconLg: Int = 18

    // Corners
    public val cornerRadius: Int = 6
    public val cornerRadiusSmall: Int = 4
    public val cornerRadiusLarge: Int = 8

    // Splitters
    public val splitterThickness: Int = 1
    public val splitterHandleHitArea: Int = 6

    // Popups
    public val popupCompletionWidth: Int = 400
    public val popupCompletionMaxHeight: Int = 300
    public val popupHoverWidthMin: Int = 200
    public val popupHoverWidthMax: Int = 500
    public val popupHoverHeightMax: Int = 300
    public val popupSearchWidth: Int = 600
    public val popupSearchHeight: Int = 480

    // Dialogs
    public val dialogSettingsWidth: Int = 900
    public val dialogSettingsHeight: Int = 650
    public val dialogSettingsNavWidth: Int = 220
    public val dialogMinWidth: Int = 480

    // Notifications
    public val notificationWidth: Int = 360
    public val notificationStripeWidth: Int = 3
    public val notificationStackPadding: Int = 12
}

/**
 * Elevation tokens — translated to shadow dp by the renderer.
 */
@Suppress("MagicNumber")
public object Elevation {
    public val flat: Int = 0
    public val toolbar: Int = 1
    public val popup: Int = 8
    public val dialog: Int = 16
}

/**
 * IntelliJ IDEA Light theme colors.
 *
 * Maintains full key parity with [IntelliJColors] so the future LocalJetaTheme
 * migration can swap palettes mechanically. **Add tokens in lockstep with the
 * dark palette.**
 */
@Suppress("LargeClass")
public object IntelliJLightColors {
    // Backgrounds
    public val background: Color = Color(0xFFF8F8F8)
    public val backgroundDarker: Color = Color(0xFFEBEBEB)
    public val backgroundLighter: Color = Color(0xFFFFFFFF)

    // Surfaces
    public val surface: Color = Color(0xFFFFFFFF)
    public val surfaceElevated: Color = Color(0xFFF2F2F2)
    public val surfaceContainer: Color = Color(0xFFEDEDED)
    public val surfaceHover: Color = Color(0xFFE5E5E5)

    // Tool window aliases
    public val toolWindowBackground: Color = surface
    public val toolWindowHeader: Color = surfaceElevated
    public val toolWindowBorder: Color = Color(0xFFD4D4D4)

    // Text
    public val textPrimary: Color = Color(0xFF1F1F1F)
    public val textSecondary: Color = Color(0xFF555555)
    public val textMuted: Color = Color(0xFF8A8A8A)
    public val textDisabled: Color = Color(0xFFB0B0B0)
    public val textLink: Color = Color(0xFF2470B3)
    public val textInverse: Color = Color(0xFFFFFFFF)

    // Accent
    public val accent: Color = Color(0xFF2D7DD2)
    public val accentHover: Color = Color(0xFF3A8AE0)
    public val accentPressed: Color = Color(0xFF246BB5)
    public val accentMuted: Color = Color(0xFFB0CCEB)
    public val accentSubtle: Color = Color(0xFFCFE0F4)

    // Semantic
    public val success: Color = Color(0xFF2EA043)
    public val successMuted: Color = Color(0xFFD7EFDD)
    public val warning: Color = Color(0xFFB58800)
    public val warningMuted: Color = Color(0xFFFAEFC8)
    public val error: Color = Color(0xFFD13438)
    public val errorMuted: Color = Color(0xFFF7D6D7)
    public val info: Color = Color(0xFF1E7AC9)
    public val infoMuted: Color = Color(0xFFCFE0F4)

    // Editor
    public val editorBackground: Color = Color(0xFFFFFFFF)
    public val editorGutter: Color = Color(0xFFF5F5F5)
    public val editorLineHighlight: Color = Color(0xFFEEF6FB)
    public val editorSelection: Color = Color(0xFFCFE0F4)
    public val editorCaretRow: Color = Color(0xFFEEF6FB)
    public val editorCurrentLine: Color = Color(0xFFEAF3FA)
    public val editorCaret: Color = Color(0xFF333333)
    public val editorSelectionActive: Color = Color(0xFFBBD6F2)
    public val editorIdentifier: Color = Color(0xFF1F1F1F)

    // Tabs
    public val tabBackground: Color = Color.Transparent
    public val tabBackgroundSelected: Color = Color(0xFFFFFFFF)
    public val tabBackgroundHover: Color = Color(0xFFEDEDED)
    public val tabBorder: Color = Color.Transparent
    public val tabUnderline: Color = accent

    // Activity bar
    public val activityBarBackground: Color = Color(0xFFEBEBEB)
    public val activityBarForeground: Color = Color(0xFF6E6E6E)
    public val activityBarForegroundActive: Color = Color(0xFF1F1F1F)
    public val activityBarIndicator: Color = accent
    public val activityBarHover: Color = Color(0xFFDFDFDF)

    // Trees
    public val treeBackground: Color = surface
    public val treeSelectionBackground: Color = Color(0xFFCFE0F4)
    public val treeSelectionInactive: Color = Color(0xFFE2E2E2)
    public val treeForeground: Color = textPrimary
    public val treeHoverBackground: Color = Color(0xFFEDEDED)
    public val treeIndentGuide: Color = Color(0xFFE2E2E2)
    public val treeSelectionAccent: Color = accent

    // Status bar
    public val statusBarBackground: Color = Color(0xFFEBEBEB)
    public val statusBarForeground: Color = textSecondary
    public val statusBarHover: Color = Color(0xFFDFDFDF)
    public val statusBarDivider: Color = Color(0xFFD4D4D4)

    // Buttons (default/secondary)
    public val buttonBackground: Color = Color(0xFFF2F2F2)
    public val buttonBackgroundHover: Color = Color(0xFFE5E5E5)
    public val buttonBackgroundPressed: Color = Color(0xFFD4D4D4)
    public val buttonForeground: Color = textPrimary

    // Buttons (primary)
    public val buttonPrimaryBackground: Color = accent
    public val buttonPrimaryBackgroundHover: Color = accentHover
    public val buttonPrimaryForeground: Color = Color(0xFFFFFFFF)

    // Buttons (danger)
    public val buttonDangerBackground: Color = Color(0xFFD13438)
    public val buttonDangerBackgroundHover: Color = Color(0xFFE04A4E)
    public val buttonDangerForeground: Color = Color(0xFFFFFFFF)

    // Inputs
    public val inputBackground: Color = Color(0xFFFFFFFF)
    public val inputBackgroundHover: Color = Color(0xFFFAFAFA)
    public val inputBorder: Color = Color(0xFFC4C4C4)
    public val inputBorderHover: Color = Color(0xFF9D9D9D)
    public val inputBorderFocused: Color = accent
    public val inputPlaceholder: Color = textMuted

    // Scrollbars
    public val scrollbarThumb: Color = Color(0xFFC4C4C4)
    public val scrollbarThumbHover: Color = Color(0xFF9D9D9D)
    public val scrollbarTrack: Color = Color.Transparent

    // Dividers
    public val divider: Color = Color(0xFFD4D4D4)
    public val border: Color = Color(0xFFC4C4C4)
    public val borderSubtle: Color = Color(0xFFE5E5E5)

    // Terminal
    public val terminalBackground: Color = Color(0xFFFFFFFF)
    public val terminalHeader: Color = surfaceElevated
    public val terminalInputBackground: Color = Color(0xFFF5F5F5)
    public val terminalForeground: Color = textPrimary
    public val terminalCursor: Color = accent
    public val terminalSelectionBackground: Color = editorSelection
    public val terminalGreen: Color = Color(0xFF2EA043)
    public val terminalRed: Color = Color(0xFFD13438)
    public val terminalYellow: Color = Color(0xFFB58800)
    public val terminalBlue: Color = Color(0xFF1E7AC9)
    public val terminalMagenta: Color = Color(0xFF9B30A8)
    public val terminalCyan: Color = Color(0xFF1A8B8B)

    // Gutter
    public val gutterBackground: Color = editorGutter
    public val lineNumberForeground: Color = Color(0xFF999999)
    public val lineNumberForegroundActive: Color = Color(0xFF333333)

    // Breadcrumbs
    public val breadcrumbsBackground: Color = Color.Transparent
    public val breadcrumbsForeground: Color = textSecondary
    public val breadcrumbsForegroundHover: Color = textPrimary
    public val breadcrumbsFileForeground: Color = textPrimary
    public val breadcrumbsBackgroundHover: Color = surfaceHover
    public val breadcrumbsSeparator: Color = textMuted

    // Icons
    public val iconDefault: Color = Color(0xFF6E6E6E)
    public val iconFolder: Color = Color(0xFFC79427)
    public val iconFile: Color = Color(0xFF2470B3)
    public val iconKotlin: Color = Color(0xFF7F52FF)
    public val iconJava: Color = Color(0xFFB85420)
    public val iconRust: Color = Color(0xFFB47A60)
    public val iconCpp: Color = Color(0xFF3A6E9C)
    public val iconVala: Color = Color(0xFF552B85)
    public val iconPython: Color = Color(0xFF2E5A85)

    public val lineNumberText: Color = lineNumberForeground

    // Popups
    public val popupBackground: Color = Color(0xFFFFFFFF)
    public val popupBorder: Color = Color(0xFFC4C4C4)
    public val popupShadow: Color = Color(0x33000000)
    public val menuItemHover: Color = Color(0xFFCFE0F4)

    // Dialogs
    public val dialogBackground: Color = Color(0xFFFFFFFF)
    public val dialogOverlay: Color = Color(0x66000000)

    // Focus / selection
    public val focusRing: Color = accent.copy(alpha = 0.4f)
    public val selectionBackground: Color = accentSubtle
    public val selectionInactive: Color = Color(0xFFE2E2E2)

    // Editor extras
    public val editorIndentGuide: Color = Color(0xFFE2E2E2)
    public val editorIndentGuideActive: Color = Color(0xFFB0B0B0)
    public val diagnosticErrorStripe: Color = error
    public val diagnosticWarningStripe: Color = warning
    public val diagnosticInfoStripe: Color = info
    public val diagnosticHintStripe: Color = textMuted

    // Notifications
    public val notificationBackground: Color = surface
    public val notificationBorder: Color = border
    public val notificationInfoStripe: Color = info
    public val notificationSuccessStripe: Color = success
    public val notificationWarningStripe: Color = warning
    public val notificationErrorStripe: Color = error

    // Welcome hub
    public val welcomeRecentRowHover: Color = Color(0xFFE9EFF6)
    public val brandGradientStart: Color = Color(0xFF5B9BD5)
    public val brandGradientEnd: Color = Color(0xFF7F52FF)
}
