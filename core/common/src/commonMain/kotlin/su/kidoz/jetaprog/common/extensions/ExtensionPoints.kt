package su.kidoz.jetaprog.common.extensions

/**
 * Well-known extension points used across the IDE.
 *
 * Plugins register implementations at these extension points to contribute
 * functionality like completion providers, settings pages, and weighers.
 */
public object ExtensionPoints {
    /**
     * Extension point for completion contributors.
     * Registered contributors are called during code completion to provide items.
     */
    public val COMPLETION_CONTRIBUTOR: ExtensionPointName<Any> =
        ExtensionPointName("su.kidoz.jetaprog.completionContributor")

    /**
     * Extension point for completion weighers.
     * Registered weighers participate in the completion sorting pipeline.
     */
    public val COMPLETION_WEIGHER: ExtensionPointName<Any> =
        ExtensionPointName("su.kidoz.jetaprog.completionWeigher")

    /**
     * Extension point for completion confidence providers.
     * Controls whether auto-popup should be shown in specific contexts.
     */
    public val COMPLETION_CONFIDENCE: ExtensionPointName<Any> =
        ExtensionPointName("su.kidoz.jetaprog.completionConfidence")

    /**
     * Extension point for settings configurables.
     * Plugins register settings pages that appear in the Settings dialog.
     */
    public val SETTINGS_CONFIGURABLE: ExtensionPointName<Any> =
        ExtensionPointName("su.kidoz.jetaprog.settingsConfigurable")

    /**
     * Extension point for settings validators.
     * Registered validators provide real-time validation for settings fields.
     */
    public val SETTINGS_VALIDATOR: ExtensionPointName<Any> =
        ExtensionPointName("su.kidoz.jetaprog.settingsValidator")
}
