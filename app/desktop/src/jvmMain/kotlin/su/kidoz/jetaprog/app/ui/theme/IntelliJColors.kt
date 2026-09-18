package su.kidoz.jetaprog.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Modern flat dark theme color palette.
 * Inspired by IntelliJ IDEA Darcula but with improved contrast and modern aesthetics.
 */
public object IntelliJColors : IntelliJPalette {
    public override val isDark: Boolean = true

    // ============================================================
    // BACKGROUND COLORS (warmer, less harsh)
    // ============================================================
    public override val background: Color = Color(0xFF1A1B1E)
    public override val backgroundDarker: Color = Color(0xFF141517)
    public override val backgroundLighter: Color = Color(0xFF252629)

    // ============================================================
    // SURFACE COLORS (subtle differentiation)
    // ============================================================
    public override val surface: Color = Color(0xFF1F2023)
    public override val surfaceElevated: Color = Color(0xFF232427)
    public override val surfaceContainer: Color = Color(0xFF282A2E)
    public override val surfaceHover: Color = Color(0xFF2D2F33)

    // Panel colors (aliased for compatibility)
    public override val toolWindowBackground: Color = surface
    public override val toolWindowHeader: Color = surfaceElevated
    public override val toolWindowBorder: Color = Color(0xFF2A2B2F)

    // ============================================================
    // TEXT COLORS (improved contrast) - must be defined early
    // ============================================================
    public override val textPrimary: Color = Color(0xFFD4D4D4)
    public override val textSecondary: Color = Color(0xFF9D9D9D)
    public override val textMuted: Color = Color(0xFF6B6B6B)
    public override val textDisabled: Color = Color(0xFF505050)
    public override val textLink: Color = Color(0xFF6BB3F8)
    public override val textInverse: Color = Color(0xFF1A1B1E)

    // ============================================================
    // ACCENT COLORS (more vibrant) - must be defined early
    // ============================================================
    public override val accent: Color = Color(0xFF5B9BD5)
    public override val accentHover: Color = Color(0xFF6BAADF)
    public override val accentPressed: Color = Color(0xFF4B8BC5)
    public override val accentMuted: Color = Color(0xFF3D5A80)
    public override val accentSubtle: Color = Color(0xFF264F78)

    // Semantic colors
    public override val success: Color = Color(0xFF4EC969)
    public override val successMuted: Color = Color(0xFF2D4A35)
    public override val warning: Color = Color(0xFFDBA800)
    public override val warningMuted: Color = Color(0xFF4A4020)
    public override val error: Color = Color(0xFFF85149)
    public override val errorMuted: Color = Color(0xFF4A2A2A)
    public override val info: Color = Color(0xFF58A6FF)
    public override val infoMuted: Color = Color(0xFF264F78)

    // ============================================================
    // EDITOR COLORS
    // ============================================================
    public override val editorBackground: Color = background
    public override val editorGutter: Color = Color(0xFF1E1F22)
    public override val editorLineHighlight: Color = Color(0xFF1E2126)
    public override val editorSelection: Color = Color(0xFF264F78)
    public override val editorCaretRow: Color = Color(0xFF1E2126)

    /** Caret-line background highlight (brighter than [editorCaretRow]). */
    public override val editorCurrentLine: Color = Color(0xFF20242B)

    /** Blinking caret bar color. */
    public override val editorCaret: Color = Color(0xFFAEAFAD)

    /** Active text-selection background inside the editor. */
    public override val editorSelectionActive: Color = Color(0xFF2D4A6B)

    /** Default identifier/body text — slightly brighter than [textPrimary] for code. */
    public override val editorIdentifier: Color = Color(0xFFD7DBE0)

    // ============================================================
    // TAB COLORS (modern flat style)
    // ============================================================
    public override val tabBackground: Color = Color.Transparent
    public override val tabBackgroundSelected: Color = Color(0xFF2D2F33)
    public override val tabBackgroundHover: Color = Color(0xFF252629)
    public override val tabBorder: Color = Color.Transparent
    public override val tabUnderline: Color = accent

    // ============================================================
    // ACTIVITY BAR (left sidebar icons)
    // ============================================================
    public override val activityBarBackground: Color = backgroundDarker
    public override val activityBarForeground: Color = Color(0xFF8B8D91)
    public override val activityBarForegroundActive: Color = Color(0xFFFFFFFF)
    public override val activityBarIndicator: Color = accent
    public override val activityBarHover: Color = Color(0xFF252629)

    // ============================================================
    // SIDEBAR/TREE
    // ============================================================
    public override val treeBackground: Color = surface
    public override val treeSelectionBackground: Color = Color(0xFF2D4F6E)
    public override val treeSelectionInactive: Color = Color(0xFF2A2D30)
    public override val treeForeground: Color = textPrimary
    public override val treeHoverBackground: Color = Color(0xFF252629)

    /** Rows for paths excluded by `.gitignore` — dimmed, but still readable. */
    public override val treeForegroundIgnored: Color = Color(0xFF6E7175)

    /** Vertical indent-guide line in the project tree (one per depth level). */
    public override val treeIndentGuide: Color = Color(0xFF2A2C30)

    /** Left accent bar drawn on the selected tree row. */
    public override val treeSelectionAccent: Color = accent

    // ============================================================
    // STATUS BAR
    // ============================================================
    public override val statusBarBackground: Color = backgroundDarker
    public override val statusBarForeground: Color = textSecondary
    public override val statusBarHover: Color = Color(0xFF252629)
    public override val statusBarDivider: Color = Color(0xFF2A2B2F)

    // ============================================================
    // BUTTON COLORS
    // ============================================================
    public override val buttonBackground: Color = Color(0xFF2D2F33)
    public override val buttonBackgroundHover: Color = Color(0xFF363840)
    public override val buttonBackgroundPressed: Color = Color(0xFF252629)
    public override val buttonForeground: Color = textPrimary

    public override val buttonPrimaryBackground: Color = accent
    public override val buttonPrimaryBackgroundHover: Color = accentHover
    public override val buttonPrimaryForeground: Color = Color(0xFFFFFFFF)

    public override val buttonDangerBackground: Color = Color(0xFF8B3038)
    public override val buttonDangerBackgroundHover: Color = Color(0xFFA03840)
    public override val buttonDangerForeground: Color = Color(0xFFFFFFFF)

    // ============================================================
    // INPUT/TEXTFIELD COLORS
    // ============================================================
    public override val inputBackground: Color = Color(0xFF1E1F22)
    public override val inputBackgroundHover: Color = Color(0xFF232427)
    public override val inputBorder: Color = Color(0xFF3D3F42)
    public override val inputBorderHover: Color = Color(0xFF4D4F52)
    public override val inputBorderFocused: Color = accent
    public override val inputPlaceholder: Color = textMuted

    // ============================================================
    // SCROLLBAR COLORS (thin, modern)
    // ============================================================
    public override val scrollbarThumb: Color = Color(0xFF4A4C50)
    public override val scrollbarThumbHover: Color = Color(0xFF5A5C60)
    public override val scrollbarTrack: Color = Color.Transparent

    // ============================================================
    // DIVIDER/BORDER
    // ============================================================
    public override val divider: Color = Color(0xFF2A2B2F)
    public override val border: Color = Color(0xFF3D3F42)
    public override val borderSubtle: Color = Color(0xFF232427)

    // ============================================================
    // TERMINAL COLORS
    // ============================================================
    public override val terminalBackground: Color = background
    public override val terminalHeader: Color = surfaceElevated
    public override val terminalInputBackground: Color = Color(0xFF2D2D30)
    public override val terminalForeground: Color = textPrimary
    public override val terminalCursor: Color = accent
    public override val terminalSelectionBackground: Color = editorSelection
    public override val terminalGreen: Color = Color(0xFF4EC9B0)
    public override val terminalRed: Color = Color(0xFFE74C3C)
    public override val terminalYellow: Color = Color(0xFFDCDCAA)
    public override val terminalBlue: Color = Color(0xFF569CD6)
    public override val terminalMagenta: Color = Color(0xFFC586C0)
    public override val terminalCyan: Color = Color(0xFF4EC9B0)

    // ============================================================
    // GUTTER COLORS
    // ============================================================
    public override val gutterBackground: Color = editorGutter
    public override val lineNumberForeground: Color = Color(0xFF5A5D63)
    public override val lineNumberForegroundActive: Color = Color(0xFFC9CDD2)

    // ============================================================
    // BREADCRUMBS
    // ============================================================
    public override val breadcrumbsBackground: Color = Color.Transparent
    public override val breadcrumbsForeground: Color = textSecondary
    public override val breadcrumbsForegroundHover: Color = textPrimary
    public override val breadcrumbsFileForeground: Color = textPrimary
    public override val breadcrumbsBackgroundHover: Color = surfaceHover
    public override val breadcrumbsSeparator: Color = textMuted

    // ============================================================
    // ICONS
    // ============================================================
    public override val iconDefault: Color = Color(0xFF9D9FA3)
    public override val iconFolder: Color = Color(0xFFD4A656)
    public override val iconFile: Color = Color(0xFF8AB4F8)
    public override val iconKotlin: Color = Color(0xFF7F52FF)
    public override val iconJava: Color = Color(0xFFE37933)
    public override val iconRust: Color = Color(0xFFDEA584)
    public override val iconCpp: Color = Color(0xFF5C8DBC)
    public override val iconVala: Color = Color(0xFF7239B3)
    public override val iconPython: Color = Color(0xFF3776AB)

    /** Git-owned files such as `.gitignore`. */
    public override val iconGit: Color = Color(0xFFF05033)

    // Line number text color (alias for navigation components)
    public override val lineNumberText: Color = lineNumberForeground

    // ============================================================
    // DROPDOWN/POPUP COLORS
    // ============================================================
    public override val popupBackground: Color = surfaceContainer
    public override val popupBorder: Color = Color(0xFF3D3F42)
    public override val popupShadow: Color = Color(0x40000000)
    public override val menuItemHover: Color = Color(0xFF2D4F6E)

    // ============================================================
    // DIALOG COLORS
    // ============================================================
    public override val dialogBackground: Color = surfaceElevated
    public override val dialogOverlay: Color = Color(0xB3000000)

    // ============================================================
    // FOCUS/SELECTION
    // ============================================================
    public override val focusRing: Color = accent.copy(alpha = 0.5f)
    public override val selectionBackground: Color = accentSubtle
    public override val selectionInactive: Color = Color(0xFF2A2D30)

    // ============================================================
    // EDITOR EXTRAS (indent guides, diagnostics)
    // ============================================================
    public override val editorIndentGuide: Color = Color(0xFF2A2B2F)
    public override val editorIndentGuideActive: Color = Color(0xFF4A4C50)
    public override val diagnosticErrorStripe: Color = error
    public override val diagnosticWarningStripe: Color = warning
    public override val diagnosticInfoStripe: Color = info
    public override val diagnosticHintStripe: Color = textMuted

    // ============================================================
    // NOTIFICATIONS (toast / banner)
    // ============================================================
    public override val notificationBackground: Color = surfaceContainer
    public override val notificationBorder: Color = border
    public override val notificationInfoStripe: Color = info
    public override val notificationSuccessStripe: Color = success
    public override val notificationWarningStripe: Color = warning
    public override val notificationErrorStripe: Color = error

    // ============================================================
    // WELCOME HUB
    // ============================================================

    /** Background of a hovered / first recent-project row. */
    public override val welcomeRecentRowHover: Color = Color(0xFF232831)

    /** Brand "J" tile gradient — start color. */
    public override val brandGradientStart: Color = Color(0xFF5B9BD5)

    /** Brand "J" tile gradient — end color. */
    public override val brandGradientEnd: Color = Color(0xFF7F52FF)

    // ============================================================
    // AGENT SURFACE
    // ============================================================
    // The AI agent reuses the brand gradient (brandGradientStart/End) as its
    // signature accent. Tokens below cover the conversation cards, inline diffs,
    // approval gate and presence bar. Keep in lockstep with IntelliJLightColors.

    /** Background of tool-call / diff / approval cards. */
    public override val toolCardBackground: Color = Color(0xFF1C1D20)

    /** Purple-tinted border for agent cards and the model/effort chip. */
    public override val agentCardBorder: Color = Color(0xFF34303F)

    /** Background of the monospace result / command code blocks inside cards. */
    public override val codeBlockBackground: Color = Color(0xFF16191A)

    /** Effort-level text in the model/effort chip. */
    public override val agentEffortText: Color = Color(0xFFB49BE0)

    /** Border of the "Proposed" diff pill. */
    public override val agentPillBorder: Color = Color(0xFF463C66)

    /** Text of the "Proposed" diff pill. */
    public override val agentPillText: Color = Color(0xFFC9B8F0)

    /** Filled background of the diff Accept button. */
    public override val diffAcceptBackground: Color = Color(0xFF3D7A4E)

    /** Added-line background in an inline diff. */
    public override val diffAddedBackground: Color = Color(0xFF1E2D22)

    /** Removed-line background in an inline diff. */
    public override val diffRemovedBackground: Color = Color(0xFF2E2122)

    /** Added-line text in an inline diff. */
    public override val diffAddedText: Color = Color(0xFFA6C9A0)

    /** Removed-line text in an inline diff. */
    public override val diffRemovedText: Color = Color(0xFFD26B6B)

    /** Added-line gutter sign in an inline diff. */
    public override val diffAddedGutter: Color = Color(0xFF7FB97A)

    /** Border of the approval (permission gate) card. */
    public override val approvalBorder: Color = Color(0xFF4A4326)

    /** Body text of the approval (permission gate) card. */
    public override val approvalText: Color = Color(0xFFE8D9A8)

    /** Border of the agent Stop button. */
    public override val agentStopBorder: Color = Color(0xFF5A3A3A)

    /** Text/icon of the agent Stop button. */
    public override val agentStopText: Color = Color(0xFFE0A0A0)

    // ============================================================
    // DEBUGGER
    // ============================================================

    /** Filled breakpoint dot. */
    public override val breakpointRed: Color = Color(0xFFDB5C5C)

    /** Full-width wash on the current execution line (warning at ~13% alpha). */
    public override val executionLineBackground: Color = Color(0x21DBA800)

    /** End-of-line inline variable value hint. */
    public override val inlineValueText: Color = Color(0xFF6E7E6B)

    /** PAUSED state chip text. */
    public override val debugPausedText: Color = Color(0xFFE0C060)

    /** RUNNING state chip text. */
    public override val debugRunningText: Color = Color(0xFF7FCB8C)

    /** Variable name in the variables/watches tree. */
    public override val debugVarName: Color = Color(0xFF9876AA)

    /** String variable value. */
    public override val debugVarString: Color = Color(0xFF6A8759)

    /** Numeric variable value. */
    public override val debugVarNumber: Color = Color(0xFF6897BB)

    /** Type/identifier variable value. */
    public override val debugVarType: Color = Color(0xFF52B8B0)

    // ============================================================
    // FILE TYPE BADGES (shared by tree icons and editor tab badges)
    // ============================================================

    /** Markup sources: XML, HTML, YAML. */
    public override val fileIconMarkup: Color = Color(0xFFCC7832)

    /** Data files: JSON, properties. */
    public override val fileIconData: Color = Color(0xFF6A8759)

    /** Markdown documents. */
    public override val fileIconMarkdown: Color = Color(0xFF6897BB)

    /** Gradle build scripts. */
    public override val fileIconGradle: Color = Color(0xFF499C54)

    /** TOML config files. */
    public override val fileIconToml: Color = Color(0xFFE76D50)

    /** JavaScript sources. */
    public override val fileIconJavascript: Color = Color(0xFFF7DF1E)

    /** TypeScript sources. */
    public override val fileIconTypescript: Color = Color(0xFF3178C6)

    // ============================================================
    // WINDOW CHROME (macOS traffic lights — OS constants, same in both palettes)
    // ============================================================

    /** macOS traffic-light close button. */
    public override val windowCloseButton: Color = Color(0xFFFF5F57)

    /** macOS traffic-light minimize button. */
    public override val windowMinimizeButton: Color = Color(0xFFFEBC2E)

    /** macOS traffic-light zoom button. */
    public override val windowZoomButton: Color = Color(0xFF28C840)
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
    public val titleBarHeight: Int = 30
    public val statusBarHeight: Int = 24
    public val menuBarHeight: Int = 32
    public val mainToolbarHeight: Int = 32

    /** Taller toolbar used when filled with chips and the Search Everywhere field. */
    public val mainToolbarHeightFilled: Int = 36

    /** Minimum width of the Search Everywhere field in the toolbar. */
    public val searchEverywhereWidth: Int = 190

    /** Default width of menu-bar / dropdown menus. */
    public val menuWidth: Int = 260

    /** Default width of the project tool window. */
    public val projectPanelWidth: Int = 264

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

    // Database tools
    public val databaseConnectionListHeight: Int = 120
    public val databaseSchemaRowHeight: Int = 24
    public val databaseQueryEditorHeight: Int = 72
    public val databaseResultCellWidth: Int = 180
    public val databaseResultHeaderHeight: Int = 42
    public val databaseResultRowHeight: Int = 26
    public val databaseConnectionDialogMinWidth: Int = 520
    public val databaseConnectionDialogMaxWidth: Int = 620
    public val databasePortFieldWidth: Int = 110

    // Git tools
    public val gitChangesHeaderHeight: Int = 26
    public val gitChangeRowHeight: Int = 24

    // Editor area
    public val tabHeight: Int = 32
    public val panelHeaderHeight: Int = 32
    public val breadcrumbsHeight: Int = 24
    public val editorGuideWidth: Int = 1
    public val editorMinimapWidth: Int = 80
    public val lineHeightCode: Int = 21

    // Controls
    public val buttonHeight: Int = 32
    public val inputHeight: Int = 36
    public val chipHeight: Int = 26
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
    public val splitterHandleHitArea: Int = 9

    // Popups
    public val popupCompletionWidth: Int = 400
    public val popupCompletionMaxHeight: Int = 300
    public val popupHoverWidthMin: Int = 200
    public val popupHoverWidthMax: Int = 500
    public val popupHoverHeightMax: Int = 300
    public val popupSearchWidth: Int = 600
    public val popupSearchHeight: Int = 480
    public val popupListWidth: Int = 500
    public val popupUsagesWidth: Int = 700
    public val popupListMaxHeight: Int = 400
    public val popupRowHeight: Int = 28
    public val popupRowHeightCompact: Int = 24

    // Selection
    public val selectionAccentWidth: Int = 2

    // Dialogs
    public val dialogSettingsWidth: Int = 900
    public val dialogSettingsHeight: Int = 650
    public val dialogSettingsNavWidth: Int = 220
    public val dialogMinWidth: Int = 480
    public val dialogFilePickerWidth: Int = 780
    public val dialogFilePickerListHeight: Int = 380
    public val dialogFilePickerPlacesWidth: Int = 160

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
public object IntelliJLightColors : IntelliJPalette {
    public override val isDark: Boolean = false

    // Backgrounds
    public override val background: Color = Color(0xFFF8F8F8)
    public override val backgroundDarker: Color = Color(0xFFEBEBEB)
    public override val backgroundLighter: Color = Color(0xFFFFFFFF)

    // Surfaces
    public override val surface: Color = Color(0xFFFFFFFF)
    public override val surfaceElevated: Color = Color(0xFFF2F2F2)
    public override val surfaceContainer: Color = Color(0xFFEDEDED)
    public override val surfaceHover: Color = Color(0xFFE5E5E5)

    // Tool window aliases
    public override val toolWindowBackground: Color = surface
    public override val toolWindowHeader: Color = surfaceElevated
    public override val toolWindowBorder: Color = Color(0xFFD4D4D4)

    // Text
    public override val textPrimary: Color = Color(0xFF1F1F1F)
    public override val textSecondary: Color = Color(0xFF555555)
    public override val textMuted: Color = Color(0xFF8A8A8A)
    public override val textDisabled: Color = Color(0xFFB0B0B0)
    public override val textLink: Color = Color(0xFF2470B3)
    public override val textInverse: Color = Color(0xFFFFFFFF)

    // Accent
    public override val accent: Color = Color(0xFF2D7DD2)
    public override val accentHover: Color = Color(0xFF3A8AE0)
    public override val accentPressed: Color = Color(0xFF246BB5)
    public override val accentMuted: Color = Color(0xFFB0CCEB)
    public override val accentSubtle: Color = Color(0xFFCFE0F4)

    // Semantic
    public override val success: Color = Color(0xFF2EA043)
    public override val successMuted: Color = Color(0xFFD7EFDD)
    public override val warning: Color = Color(0xFFB58800)
    public override val warningMuted: Color = Color(0xFFFAEFC8)
    public override val error: Color = Color(0xFFD13438)
    public override val errorMuted: Color = Color(0xFFF7D6D7)
    public override val info: Color = Color(0xFF1E7AC9)
    public override val infoMuted: Color = Color(0xFFCFE0F4)

    // Editor
    public override val editorBackground: Color = Color(0xFFFFFFFF)
    public override val editorGutter: Color = Color(0xFFF5F5F5)
    public override val editorLineHighlight: Color = Color(0xFFEEF6FB)
    public override val editorSelection: Color = Color(0xFFCFE0F4)
    public override val editorCaretRow: Color = Color(0xFFEEF6FB)
    public override val editorCurrentLine: Color = Color(0xFFEAF3FA)
    public override val editorCaret: Color = Color(0xFF333333)
    public override val editorSelectionActive: Color = Color(0xFFBBD6F2)
    public override val editorIdentifier: Color = Color(0xFF1F1F1F)

    // Tabs
    public override val tabBackground: Color = Color.Transparent
    public override val tabBackgroundSelected: Color = Color(0xFFFFFFFF)
    public override val tabBackgroundHover: Color = Color(0xFFEDEDED)
    public override val tabBorder: Color = Color.Transparent
    public override val tabUnderline: Color = accent

    // Activity bar
    public override val activityBarBackground: Color = Color(0xFFEBEBEB)
    public override val activityBarForeground: Color = Color(0xFF6E6E6E)
    public override val activityBarForegroundActive: Color = Color(0xFF1F1F1F)
    public override val activityBarIndicator: Color = accent
    public override val activityBarHover: Color = Color(0xFFDFDFDF)

    // Trees
    public override val treeBackground: Color = surface
    public override val treeSelectionBackground: Color = Color(0xFFCFE0F4)
    public override val treeSelectionInactive: Color = Color(0xFFE2E2E2)
    public override val treeForeground: Color = textPrimary

    /** Rows for paths excluded by `.gitignore` — dimmed, but still readable. */
    public override val treeForegroundIgnored: Color = Color(0xFF8C8C8C)
    public override val treeHoverBackground: Color = Color(0xFFEDEDED)
    public override val treeIndentGuide: Color = Color(0xFFE2E2E2)
    public override val treeSelectionAccent: Color = accent

    // Status bar
    public override val statusBarBackground: Color = Color(0xFFEBEBEB)
    public override val statusBarForeground: Color = textSecondary
    public override val statusBarHover: Color = Color(0xFFDFDFDF)
    public override val statusBarDivider: Color = Color(0xFFD4D4D4)

    // Buttons (default/secondary)
    public override val buttonBackground: Color = Color(0xFFF2F2F2)
    public override val buttonBackgroundHover: Color = Color(0xFFE5E5E5)
    public override val buttonBackgroundPressed: Color = Color(0xFFD4D4D4)
    public override val buttonForeground: Color = textPrimary

    // Buttons (primary)
    public override val buttonPrimaryBackground: Color = accent
    public override val buttonPrimaryBackgroundHover: Color = accentHover
    public override val buttonPrimaryForeground: Color = Color(0xFFFFFFFF)

    // Buttons (danger)
    public override val buttonDangerBackground: Color = Color(0xFFD13438)
    public override val buttonDangerBackgroundHover: Color = Color(0xFFE04A4E)
    public override val buttonDangerForeground: Color = Color(0xFFFFFFFF)

    // Inputs
    public override val inputBackground: Color = Color(0xFFFFFFFF)
    public override val inputBackgroundHover: Color = Color(0xFFFAFAFA)
    public override val inputBorder: Color = Color(0xFFC4C4C4)
    public override val inputBorderHover: Color = Color(0xFF9D9D9D)
    public override val inputBorderFocused: Color = accent
    public override val inputPlaceholder: Color = textMuted

    // Scrollbars
    public override val scrollbarThumb: Color = Color(0xFFC4C4C4)
    public override val scrollbarThumbHover: Color = Color(0xFF9D9D9D)
    public override val scrollbarTrack: Color = Color.Transparent

    // Dividers
    public override val divider: Color = Color(0xFFD4D4D4)
    public override val border: Color = Color(0xFFC4C4C4)
    public override val borderSubtle: Color = Color(0xFFE5E5E5)

    // Terminal
    public override val terminalBackground: Color = Color(0xFFFFFFFF)
    public override val terminalHeader: Color = surfaceElevated
    public override val terminalInputBackground: Color = Color(0xFFF5F5F5)
    public override val terminalForeground: Color = textPrimary
    public override val terminalCursor: Color = accent
    public override val terminalSelectionBackground: Color = editorSelection
    public override val terminalGreen: Color = Color(0xFF2EA043)
    public override val terminalRed: Color = Color(0xFFD13438)
    public override val terminalYellow: Color = Color(0xFFB58800)
    public override val terminalBlue: Color = Color(0xFF1E7AC9)
    public override val terminalMagenta: Color = Color(0xFF9B30A8)
    public override val terminalCyan: Color = Color(0xFF1A8B8B)

    // Gutter
    public override val gutterBackground: Color = editorGutter
    public override val lineNumberForeground: Color = Color(0xFF999999)
    public override val lineNumberForegroundActive: Color = Color(0xFF333333)

    // Breadcrumbs
    public override val breadcrumbsBackground: Color = Color.Transparent
    public override val breadcrumbsForeground: Color = textSecondary
    public override val breadcrumbsForegroundHover: Color = textPrimary
    public override val breadcrumbsFileForeground: Color = textPrimary
    public override val breadcrumbsBackgroundHover: Color = surfaceHover
    public override val breadcrumbsSeparator: Color = textMuted

    // Icons
    public override val iconDefault: Color = Color(0xFF6E6E6E)
    public override val iconFolder: Color = Color(0xFFC79427)
    public override val iconFile: Color = Color(0xFF2470B3)
    public override val iconKotlin: Color = Color(0xFF7F52FF)
    public override val iconJava: Color = Color(0xFFB85420)
    public override val iconRust: Color = Color(0xFFB47A60)
    public override val iconCpp: Color = Color(0xFF3A6E9C)
    public override val iconVala: Color = Color(0xFF552B85)
    public override val iconPython: Color = Color(0xFF2E5A85)

    /** Git-owned files such as `.gitignore`. */
    public override val iconGit: Color = Color(0xFFC63A20)

    public override val lineNumberText: Color = lineNumberForeground

    // Popups
    public override val popupBackground: Color = Color(0xFFFFFFFF)
    public override val popupBorder: Color = Color(0xFFC4C4C4)
    public override val popupShadow: Color = Color(0x33000000)
    public override val menuItemHover: Color = Color(0xFFCFE0F4)

    // Dialogs
    public override val dialogBackground: Color = Color(0xFFFFFFFF)
    public override val dialogOverlay: Color = Color(0x66000000)

    // Focus / selection
    public override val focusRing: Color = accent.copy(alpha = 0.4f)
    public override val selectionBackground: Color = accentSubtle
    public override val selectionInactive: Color = Color(0xFFE2E2E2)

    // Editor extras
    public override val editorIndentGuide: Color = Color(0xFFE2E2E2)
    public override val editorIndentGuideActive: Color = Color(0xFFB0B0B0)
    public override val diagnosticErrorStripe: Color = error
    public override val diagnosticWarningStripe: Color = warning
    public override val diagnosticInfoStripe: Color = info
    public override val diagnosticHintStripe: Color = textMuted

    // Notifications
    public override val notificationBackground: Color = surface
    public override val notificationBorder: Color = border
    public override val notificationInfoStripe: Color = info
    public override val notificationSuccessStripe: Color = success
    public override val notificationWarningStripe: Color = warning
    public override val notificationErrorStripe: Color = error

    // Welcome hub
    public override val welcomeRecentRowHover: Color = Color(0xFFE9EFF6)
    public override val brandGradientStart: Color = Color(0xFF5B9BD5)
    public override val brandGradientEnd: Color = Color(0xFF7F52FF)

    // Agent surface (parity with IntelliJColors)
    public override val toolCardBackground: Color = Color(0xFFF2F2F2)
    public override val agentCardBorder: Color = Color(0xFFD8CEEC)
    public override val codeBlockBackground: Color = Color(0xFFF1F2F4)
    public override val agentEffortText: Color = Color(0xFF6A4BB0)
    public override val agentPillBorder: Color = Color(0xFFB7A4E8)
    public override val agentPillText: Color = Color(0xFF6A4BB0)
    public override val diffAcceptBackground: Color = Color(0xFF2EA043)
    public override val diffAddedBackground: Color = Color(0xFFE2F4E6)
    public override val diffRemovedBackground: Color = Color(0xFFFBE3E4)
    public override val diffAddedText: Color = Color(0xFF2E7D43)
    public override val diffRemovedText: Color = Color(0xFFC0392B)
    public override val diffAddedGutter: Color = Color(0xFF2EA043)
    public override val approvalBorder: Color = Color(0xFFE6D9A6)
    public override val approvalText: Color = Color(0xFF7A6A20)
    public override val agentStopBorder: Color = Color(0xFFE0B4B4)
    public override val agentStopText: Color = Color(0xFFB23B3B)

    // Debugger (parity with IntelliJColors)
    public override val breakpointRed: Color = Color(0xFFDB5C5C)
    public override val executionLineBackground: Color = Color(0x29B58800)
    public override val inlineValueText: Color = Color(0xFF5E7A5B)
    public override val debugPausedText: Color = Color(0xFF8A6D00)
    public override val debugRunningText: Color = Color(0xFF2E7D43)
    public override val debugVarName: Color = Color(0xFF6A4BA0)
    public override val debugVarString: Color = Color(0xFF2E7D43)
    public override val debugVarNumber: Color = Color(0xFF1E66A8)
    public override val debugVarType: Color = Color(0xFF2A8A86)

    // File type badges (brand colors, shared with the dark palette)
    public override val fileIconMarkup: Color = Color(0xFFCC7832)
    public override val fileIconData: Color = Color(0xFF6A8759)
    public override val fileIconMarkdown: Color = Color(0xFF6897BB)
    public override val fileIconGradle: Color = Color(0xFF499C54)
    public override val fileIconToml: Color = Color(0xFFE76D50)
    public override val fileIconJavascript: Color = Color(0xFFF7DF1E)
    public override val fileIconTypescript: Color = Color(0xFF3178C6)

    // Window chrome (parity with IntelliJColors — OS constants)
    public override val windowCloseButton: Color = Color(0xFFFF5F57)
    public override val windowMinimizeButton: Color = Color(0xFFFEBC2E)
    public override val windowZoomButton: Color = Color(0xFF28C840)
}
