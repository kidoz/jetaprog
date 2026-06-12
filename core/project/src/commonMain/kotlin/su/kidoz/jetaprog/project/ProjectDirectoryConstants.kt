package su.kidoz.jetaprog.project

/**
 * Constants for the JetaProg project directory structure.
 */
public object ProjectDirectoryConstants {
    /**
     * Name of the project directory.
     */
    public const val DIRECTORY_NAME: String = ".jetaprog"

    // Root-level files
    public const val PROJECT_CONFIG_FILE: String = "project.json"
    public const val WORKSPACE_STATE_FILE: String = "workspace.json"
    public const val MODULES_CONFIG_FILE: String = "modules.json"

    // Subdirectories
    public const val SETTINGS_DIR: String = "settings"
    public const val INDEX_DIR: String = "index"
    public const val STATE_DIR: String = "state"
    public const val CACHE_DIR: String = "cache"
    public const val PLUGINS_DIR: String = "plugins"

    // Settings files
    public const val CODE_STYLE_FILE: String = "code-style.json"
    public const val INSPECTIONS_FILE: String = "inspections.json"
    public const val RUN_CONFIGS_FILE: String = "run-configs.json"
    public const val FILE_TEMPLATES_FILE: String = "file-templates.json"
    public const val DICTIONARIES_DIR: String = "dictionaries"

    // Index files
    public const val SYMBOLS_DB_FILE: String = "symbols.db"
    public const val REFERENCES_DB_FILE: String = "references.db"
    public const val FILE_HASHES_FILE: String = "file-hashes.json"
    public const val AST_CACHE_DIR: String = "ast-cache"

    // State files
    public const val EDITOR_STATE_FILE: String = "editor-state.json"
    public const val PANEL_LAYOUT_FILE: String = "panel-layout.json"
    public const val RECENT_FILES_FILE: String = "recent-files.json"
    public const val SEARCH_HISTORY_FILE: String = "search-history.json"
    public const val TERMINAL_HISTORY_FILE: String = "terminal-history.json"
    public const val BREAKPOINTS_FILE: String = "breakpoints.json"

    // Cache directories
    public const val SYNTAX_TOKENS_CACHE_DIR: String = "syntax-tokens"
    public const val COMPLETIONS_CACHE_DIR: String = "completions"
    public const val DIAGNOSTICS_CACHE_DIR: String = "diagnostics"
    public const val THUMBNAILS_CACHE_DIR: String = "thumbnails"

    /**
     * Files and directories that should be committed to version control.
     */
    public val SHAREABLE_FILES: Set<String> =
        setOf(
            PROJECT_CONFIG_FILE,
            MODULES_CONFIG_FILE,
            SETTINGS_DIR,
        )

    /**
     * Files and directories that should NOT be committed (user-specific).
     */
    public val GITIGNORE_ENTRIES: List<String> =
        listOf(
            "$DIRECTORY_NAME/$WORKSPACE_STATE_FILE",
            "$DIRECTORY_NAME/$INDEX_DIR/",
            "$DIRECTORY_NAME/$STATE_DIR/",
            "$DIRECTORY_NAME/$CACHE_DIR/",
            "$DIRECTORY_NAME/$PLUGINS_DIR/*/state.json",
        )
}
