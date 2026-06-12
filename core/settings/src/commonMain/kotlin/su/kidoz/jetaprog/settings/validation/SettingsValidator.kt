package su.kidoz.jetaprog.settings.validation

/**
 * Severity level for a validation result.
 */
public enum class ValidationSeverity {
    /**
     * The value is valid.
     */
    OK,

    /**
     * The value has a warning (still valid but not ideal).
     */
    WARNING,

    /**
     * The value is invalid and cannot be saved.
     */
    ERROR,
}

/**
 * Result of validating a settings field value.
 */
public data class ValidationResult(
    /**
     * The severity of the validation result.
     */
    val severity: ValidationSeverity,
    /**
     * A human-readable message describing the issue, or null if OK.
     */
    val message: String? = null,
) {
    public companion object {
        /**
         * A successful validation result.
         */
        public val OK: ValidationResult = ValidationResult(ValidationSeverity.OK)

        /**
         * Creates a warning validation result.
         */
        public fun warning(message: String): ValidationResult = ValidationResult(ValidationSeverity.WARNING, message)

        /**
         * Creates an error validation result.
         */
        public fun error(message: String): ValidationResult = ValidationResult(ValidationSeverity.ERROR, message)
    }
}

/**
 * Validates a settings field value.
 *
 * Implementations check if a settings field value is valid and return
 * a [ValidationResult] with the severity and optional message.
 */
public fun interface SettingsFieldValidator {
    /**
     * Validates a field value.
     *
     * @param value The value to validate (as a string representation)
     * @return The validation result
     */
    public fun validate(value: String): ValidationResult
}

/**
 * Registry for settings field validators.
 *
 * Maps settings paths (e.g., "editor.tabSize") to their validators.
 * The registry supports multiple validators per field (all must pass).
 */
public class SettingsValidationRegistry {
    private val validators = mutableMapOf<String, MutableList<SettingsFieldValidator>>()

    /**
     * Registers a validator for a settings field.
     *
     * @param path The settings path (e.g., "editor.tabSize")
     * @param validator The validator to register
     */
    public fun register(
        path: String,
        validator: SettingsFieldValidator,
    ) {
        validators.getOrPut(path) { mutableListOf() }.add(validator)
    }

    /**
     * Validates a settings field value.
     *
     * Runs all registered validators for the path and returns the most
     * severe result.
     *
     * @param path The settings path
     * @param value The value to validate
     * @return The validation result (worst severity wins)
     */
    public fun validate(
        path: String,
        value: String,
    ): ValidationResult {
        val fieldValidators = validators[path] ?: return ValidationResult.OK
        var worstResult = ValidationResult.OK

        for (validator in fieldValidators) {
            val result = validator.validate(value)
            if (result.severity.ordinal > worstResult.severity.ordinal) {
                worstResult = result
            }
        }

        return worstResult
    }

    /**
     * Validates all fields in a batch.
     *
     * @param values Map of settings path to value
     * @return Map of settings path to validation result (only non-OK results)
     */
    public fun validateAll(values: Map<String, String>): Map<String, ValidationResult> =
        values
            .mapNotNull { (path, value) ->
                val result = validate(path, value)
                if (result.severity != ValidationSeverity.OK) path to result else null
            }.toMap()

    init {
        registerDefaultValidators()
    }

    private fun registerDefaultValidators() {
        // Tab size: 1-16
        register("editor.tabSize") { value ->
            val num = value.toIntOrNull()
            when {
                num == null -> ValidationResult.error("Tab size must be a number")
                num < 1 -> ValidationResult.error("Tab size must be at least 1")
                num > 16 -> ValidationResult.warning("Tab size larger than 16 is unusual")
                else -> ValidationResult.OK
            }
        }

        // Font size: 6-72
        register("appearance.fontSize") { value ->
            val num = value.toIntOrNull()
            when {
                num == null -> ValidationResult.error("Font size must be a number")
                num < 6 -> ValidationResult.error("Font size must be at least 6")
                num > 72 -> ValidationResult.warning("Font size larger than 72 may cause rendering issues")
                else -> ValidationResult.OK
            }
        }

        // UI scale: 0.5-3.0
        register("appearance.uiScale") { value ->
            val num = value.toFloatOrNull()
            when {
                num == null -> ValidationResult.error("UI scale must be a number")
                num < 0.5f -> ValidationResult.error("UI scale must be at least 0.5")
                num > 3.0f -> ValidationResult.warning("UI scale larger than 3.0 may cause layout issues")
                else -> ValidationResult.OK
            }
        }

        // Max line length: 0 or 40-500
        register("editor.maxLineLength") { value ->
            val num = value.toIntOrNull()
            when {
                num == null -> ValidationResult.error("Max line length must be a number")

                num == 0 -> ValidationResult.OK

                // disabled
                num < 40 -> ValidationResult.warning("Max line length below 40 is very restrictive")

                num > 500 -> ValidationResult.warning("Max line length above 500 is unusual")

                else -> ValidationResult.OK
            }
        }

        // Auto-save delay: 100-60000ms
        register("editor.autoSaveDelayMs") { value ->
            val num = value.toLongOrNull()
            when {
                num == null -> ValidationResult.error("Auto-save delay must be a number")
                num < 100 -> ValidationResult.warning("Very short auto-save delay may impact performance")
                num > 60000 -> ValidationResult.warning("Auto-save delay longer than 1 minute may lose work")
                else -> ValidationResult.OK
            }
        }
    }
}
