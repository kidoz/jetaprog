package su.kidoz.jetaprog.configuration

import kotlinx.serialization.Serializable
import su.kidoz.jetaprog.common.mvi.Effect
import su.kidoz.jetaprog.common.mvi.Intent
import su.kidoz.jetaprog.common.mvi.State

/**
 * State for run configuration management.
 */
@Serializable
public data class ConfigurationState(
    /** All available configurations. */
    val configurations: List<RunConfiguration> = emptyList(),
    /** Currently active/selected configuration ID. */
    val activeConfigurationId: ConfigurationId? = null,
    /** Recently used configuration IDs (most recent first). */
    val recentConfigurationIds: List<ConfigurationId> = emptyList(),
    /** Currently running configuration ID. */
    val runningConfigurationId: ConfigurationId? = null,
    /** Whether configuration is currently executing. */
    val isRunning: Boolean = false,
    /** Whether the configuration dialog is open. */
    val isDialogOpen: Boolean = false,
    /** Configuration being edited in dialog. */
    val editingConfiguration: RunConfiguration? = null,
    /** Error message if any. */
    val error: String? = null,
) : State {
    /** Get the active configuration. */
    val activeConfiguration: RunConfiguration?
        get() =
            activeConfigurationId?.let { id ->
                configurations.find { it.id == id }
            }

    /** Get the running configuration. */
    val runningConfiguration: RunConfiguration?
        get() =
            runningConfigurationId?.let { id ->
                configurations.find { it.id == id }
            }

    /** Get permanent configurations (non-temporary). */
    val permanentConfigurations: List<RunConfiguration>
        get() = configurations.filter { !it.isTemporary }

    /** Get temporary configurations. */
    val temporaryConfigurations: List<RunConfiguration>
        get() = configurations.filter { it.isTemporary }

    /** Get configurations grouped by folder. */
    val configurationsByFolder: Map<String?, List<RunConfiguration>>
        get() = configurations.groupBy { it.folderName }

    /** Get configurations grouped by type. */
    val configurationsByType: Map<ConfigurationType, List<RunConfiguration>>
        get() = configurations.groupBy { it.type }

    /** Maximum number of temporary configurations. */
    public companion object {
        public const val MAX_TEMPORARY_CONFIGURATIONS: Int = 5
        public const val MAX_RECENT_CONFIGURATIONS: Int = 10
    }
}

/**
 * Intents for configuration management.
 */
public sealed interface ConfigurationIntent : Intent {
    /** Load configurations from storage. */
    public data class Initialize(
        val projectPath: String,
    ) : ConfigurationIntent

    /** Select a configuration as active. */
    public data class SelectConfiguration(
        val id: ConfigurationId,
    ) : ConfigurationIntent

    /** Run the active configuration. */
    public data object RunActive : ConfigurationIntent

    /** Debug the active configuration. */
    public data object DebugActive : ConfigurationIntent

    /** Run a specific configuration. */
    public data class Run(
        val id: ConfigurationId,
    ) : ConfigurationIntent

    /** Stop the running configuration. */
    public data object Stop : ConfigurationIntent

    /** Create a new configuration. */
    public data class Create(
        val configuration: RunConfiguration,
    ) : ConfigurationIntent

    /** Update an existing configuration. */
    public data class Update(
        val configuration: RunConfiguration,
    ) : ConfigurationIntent

    /** Delete a configuration. */
    public data class Delete(
        val id: ConfigurationId,
    ) : ConfigurationIntent

    /** Duplicate a configuration. */
    public data class Duplicate(
        val id: ConfigurationId,
    ) : ConfigurationIntent

    /** Make a temporary configuration permanent. */
    public data class MakePermanent(
        val id: ConfigurationId,
    ) : ConfigurationIntent

    /** Move configuration to folder. */
    public data class MoveToFolder(
        val id: ConfigurationId,
        val folderName: String?,
    ) : ConfigurationIntent

    /** Open the configuration dialog. */
    public data object OpenDialog : ConfigurationIntent

    /** Open dialog to edit specific configuration. */
    public data class EditConfiguration(
        val id: ConfigurationId,
    ) : ConfigurationIntent

    /** Open dialog to create new configuration of type. */
    public data class CreateNew(
        val type: ConfigurationType,
    ) : ConfigurationIntent

    /** Open dialog to create a recommended configuration based on project type. */
    public data object CreateRecommended : ConfigurationIntent

    /** Close the configuration dialog. */
    public data object CloseDialog : ConfigurationIntent

    /** Save changes from dialog. */
    public data class SaveFromDialog(
        val configuration: RunConfiguration,
    ) : ConfigurationIntent

    /** Clear error. */
    public data object ClearError : ConfigurationIntent

    /** Auto-discover configurations from project. */
    public data class DiscoverConfigurations(
        val projectPath: String,
    ) : ConfigurationIntent
}

/**
 * Side effects from configuration operations.
 */
public sealed interface ConfigurationEffect : Effect {
    /** Configuration started running. */
    public data class ConfigurationStarted(
        val configuration: RunConfiguration,
    ) : ConfigurationEffect

    /** Configuration finished. */
    public data class ConfigurationFinished(
        val configuration: RunConfiguration,
        val success: Boolean,
        val exitCode: Int,
    ) : ConfigurationEffect

    /** Show error notification. */
    public data class ShowError(
        val message: String,
    ) : ConfigurationEffect

    /** Show success notification. */
    public data class ShowSuccess(
        val message: String,
    ) : ConfigurationEffect

    /** Navigate to configuration output. */
    public data class ShowOutput(
        val configurationId: ConfigurationId,
    ) : ConfigurationEffect

    /** Configurations loaded from storage. */
    public data class ConfigurationsLoaded(
        val count: Int,
    ) : ConfigurationEffect

    /** Configuration saved. */
    public data class ConfigurationSaved(
        val configuration: RunConfiguration,
    ) : ConfigurationEffect
}
