package su.kidoz.jetaprog.project.config

import kotlinx.serialization.Serializable

/**
 * Code style settings stored in `.jetaprog/settings/code-style.json`.
 * This file should be committed to version control.
 */
@Serializable
public data class CodeStyleConfig(
    /**
     * Indentation settings.
     */
    val indentation: IndentationConfig = IndentationConfig(),
    /**
     * Line length limit.
     */
    val maxLineLength: Int = 120,
    /**
     * Whether to insert final newline.
     */
    val insertFinalNewline: Boolean = true,
    /**
     * Whether to trim trailing whitespace.
     */
    val trimTrailingWhitespace: Boolean = true,
    /**
     * Brace style.
     */
    val braceStyle: BraceStyle = BraceStyle.END_OF_LINE,
    /**
     * Language-specific overrides.
     */
    val languageOverrides: Map<String, LanguageCodeStyle> = emptyMap(),
)

/**
 * Indentation configuration.
 */
@Serializable
public data class IndentationConfig(
    /**
     * Use spaces instead of tabs.
     */
    val useSpaces: Boolean = true,
    /**
     * Number of spaces per indent level.
     */
    val indentSize: Int = 4,
    /**
     * Tab width for display.
     */
    val tabWidth: Int = 4,
    /**
     * Continuation indent size.
     */
    val continuationIndent: Int = 8,
)

/**
 * Brace placement style.
 */
@Serializable
public enum class BraceStyle {
    /**
     * Opening brace at end of line (K&R style).
     */
    END_OF_LINE,

    /**
     * Opening brace on next line (Allman style).
     */
    NEXT_LINE,

    /**
     * Opening brace on next line, indented (Whitesmiths style).
     */
    NEXT_LINE_INDENTED,
}

/**
 * Language-specific code style overrides.
 */
@Serializable
public data class LanguageCodeStyle(
    /**
     * Override indentation settings.
     */
    val indentation: IndentationConfig? = null,
    /**
     * Override max line length.
     */
    val maxLineLength: Int? = null,
    /**
     * Override brace style.
     */
    val braceStyle: BraceStyle? = null,
)

/**
 * Inspection/linter settings stored in `.jetaprog/settings/inspections.json`.
 * This file should be committed to version control.
 */
@Serializable
public data class InspectionsConfig(
    /**
     * Global inspection severity level.
     */
    val defaultSeverity: InspectionSeverity = InspectionSeverity.WARNING,
    /**
     * Whether inspections are enabled.
     */
    val enabled: Boolean = true,
    /**
     * Individual inspection configurations.
     */
    val inspections: Map<String, InspectionConfig> = emptyMap(),
    /**
     * File patterns to exclude from inspections.
     */
    val excludePatterns: List<String> = emptyList(),
)

/**
 * Configuration for a single inspection.
 */
@Serializable
public data class InspectionConfig(
    /**
     * Whether this inspection is enabled.
     */
    val enabled: Boolean = true,
    /**
     * Severity level for this inspection.
     */
    val severity: InspectionSeverity? = null,
    /**
     * Additional options for this inspection.
     */
    val options: Map<String, String> = emptyMap(),
)

/**
 * Inspection severity levels.
 */
@Serializable
public enum class InspectionSeverity {
    /**
     * Error - must be fixed.
     */
    ERROR,

    /**
     * Warning - should be fixed.
     */
    WARNING,

    /**
     * Weak warning - consider fixing.
     */
    WEAK_WARNING,

    /**
     * Information - just a hint.
     */
    INFO,

    /**
     * Disabled - inspection is off.
     */
    DISABLED,
}

/**
 * Run configuration stored in `.jetaprog/settings/run-configs.json`.
 * This file should be committed to version control.
 */
@Serializable
public data class RunConfigsFile(
    /**
     * List of run configurations.
     */
    val configurations: List<RunConfiguration> = emptyList(),
    /**
     * Default configuration name.
     */
    val defaultConfiguration: String? = null,
)

/**
 * A single run/debug configuration.
 */
@Serializable
public data class RunConfiguration(
    /**
     * Configuration name.
     */
    val name: String,
    /**
     * Configuration type.
     */
    val type: RunConfigurationType,
    /**
     * Main class or script to run.
     */
    val mainClass: String? = null,
    /**
     * Module to run from.
     */
    val module: String? = null,
    /**
     * Program arguments.
     */
    val programArguments: String = "",
    /**
     * VM options (for JVM-based configs).
     */
    val vmOptions: String = "",
    /**
     * Environment variables.
     */
    val environment: Map<String, String> = emptyMap(),
    /**
     * Working directory.
     */
    val workingDirectory: String? = null,
    /**
     * Whether to use classpath of module.
     */
    val useModuleClasspath: Boolean = true,
)

/**
 * Run configuration types.
 */
@Serializable
public enum class RunConfigurationType {
    /**
     * Application with main() method.
     */
    APPLICATION,

    /**
     * JUnit/test runner.
     */
    TEST,

    /**
     * Gradle task.
     */
    GRADLE,

    /**
     * Shell script.
     */
    SHELL,

    /**
     * Remote debug.
     */
    REMOTE_DEBUG,

    /**
     * Custom command.
     */
    CUSTOM,
}
