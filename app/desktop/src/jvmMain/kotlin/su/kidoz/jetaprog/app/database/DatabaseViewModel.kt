package su.kidoz.jetaprog.app.database

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.common.mvi.Effect
import su.kidoz.jetaprog.common.mvi.Intent
import su.kidoz.jetaprog.common.mvi.MviViewModel
import su.kidoz.jetaprog.common.mvi.State
import su.kidoz.jetaprog.database.DatabaseConnectionInfo
import su.kidoz.jetaprog.database.DatabaseConnectionProfile
import su.kidoz.jetaprog.database.DatabaseCredential
import su.kidoz.jetaprog.database.DatabaseExecutionService
import su.kidoz.jetaprog.database.DatabaseProfileStore
import su.kidoz.jetaprog.database.DatabaseQueryResult
import su.kidoz.jetaprog.database.DatabaseSchema
import su.kidoz.jetaprog.database.SessionDatabaseCredentialStore

/** Database operation currently occupying the shared execution service. */
public enum class DatabaseOperation {
    /** No operation is active. */
    IDLE,

    /** A connection test is active. */
    TESTING,

    /** Schema metadata is being imported. */
    INTROSPECTING,

    /** A SQL statement is executing. */
    EXECUTING,
}

/** Immutable state for the Database navigator and Query Results tool window. */
@Immutable
public data class DatabaseState(
    /** Persisted password-free connection profiles. */
    val profiles: List<DatabaseConnectionProfile> = emptyList(),
    /** Selected connection profile identifier. */
    val selectedProfileId: String? = null,
    /** Schema metadata for the selected profile. */
    val schemas: List<DatabaseSchema> = emptyList(),
    /** SQL text in the query editor. */
    val query: String = "SELECT 1;",
    /** Most recent query result. */
    val result: DatabaseQueryResult? = null,
    /** Current shared database operation. */
    val operation: DatabaseOperation = DatabaseOperation.IDLE,
    /** Last successful connection-test information. */
    val connectionInfo: DatabaseConnectionInfo? = null,
    /** User-facing failure message. */
    val error: String? = null,
    /** Whether profiles are being loaded from disk. */
    val isLoadingProfiles: Boolean = true,
) : State {
    /** Currently selected connection profile. */
    public val selectedProfile: DatabaseConnectionProfile?
        get() = profiles.firstOrNull { it.id == selectedProfileId }

    /** Whether the shared service currently accepts cancellation. */
    public val isBusy: Boolean
        get() = operation != DatabaseOperation.IDLE
}

/** User and lifecycle actions handled by [DatabaseViewModel]. */
public sealed interface DatabaseIntent : Intent {
    /** Loads saved connection profiles. */
    public data object LoadProfiles : DatabaseIntent

    /** Selects a profile without starting network activity. */
    public data class SelectProfile(
        /** Profile identifier. */
        val profileId: String,
    ) : DatabaseIntent

    /** Saves a connection profile and optionally remembers its password for this process. */
    public data class SaveProfile(
        /** Password-free profile settings. */
        val profile: DatabaseConnectionProfile,
        /** Transient password, or null to preserve the existing session password. */
        val credential: DatabaseCredential?,
    ) : DatabaseIntent

    /** Deletes a connection profile. */
    public data class DeleteProfile(
        /** Profile identifier. */
        val profileId: String,
    ) : DatabaseIntent

    /** Tests the selected connection. */
    public data object TestConnection : DatabaseIntent

    /** Reloads schema metadata for the selected connection. */
    public data object RefreshSchema : DatabaseIntent

    /** Replaces the SQL editor text. */
    public data class UpdateQuery(
        /** New SQL text. */
        val query: String,
    ) : DatabaseIntent

    /** Executes the current SQL text. */
    public data object ExecuteQuery : DatabaseIntent

    /** Cancels the current database operation. */
    public data object CancelOperation : DatabaseIntent

    /** Clears the latest result and error. */
    public data object ClearResult : DatabaseIntent
}

/** One-time events emitted by [DatabaseViewModel]. */
public sealed interface DatabaseEffect : Effect {
    /** Requests that the Query Results bottom tool window be shown. */
    public data object OpenQueryResults : DatabaseEffect

    /** Displays an informational notification. */
    public data class ShowMessage(
        /** Notification message. */
        val message: String,
    ) : DatabaseEffect
}

/** Coordinates profile persistence and all cancellable database operations. */
public class DatabaseViewModel(
    private val profileStore: DatabaseProfileStore,
    private val credentialStore: SessionDatabaseCredentialStore,
    private val executionService: DatabaseExecutionService,
) : MviViewModel<DatabaseIntent, DatabaseState, DatabaseEffect>(DatabaseState()) {
    private var operationJob: Job? = null
    private var operationGeneration: Long = 0L

    init {
        dispatch(DatabaseIntent.LoadProfiles)
    }

    override suspend fun handleIntent(intent: DatabaseIntent) {
        when (intent) {
            DatabaseIntent.LoadProfiles -> loadProfiles()
            is DatabaseIntent.SelectProfile -> selectProfile(intent.profileId)
            is DatabaseIntent.SaveProfile -> saveProfile(intent.profile, intent.credential)
            is DatabaseIntent.DeleteProfile -> deleteProfile(intent.profileId)
            DatabaseIntent.TestConnection -> testConnection()
            DatabaseIntent.RefreshSchema -> refreshSchema()
            is DatabaseIntent.UpdateQuery -> updateState { copy(query = intent.query) }
            DatabaseIntent.ExecuteQuery -> executeQuery()
            DatabaseIntent.CancelOperation -> cancelOperation()
            DatabaseIntent.ClearResult -> updateState { copy(result = null, error = null) }
        }
    }

    override fun dispose() {
        operationJob?.cancel()
        executionService.dispose()
        super.dispose()
    }

    private suspend fun loadProfiles() {
        profileStore
            .load()
            .onSuccess { profiles ->
                updateState {
                    copy(
                        profiles = profiles,
                        selectedProfileId = selectedProfileId?.takeIf { id -> profiles.any { it.id == id } },
                        isLoadingProfiles = false,
                        error = null,
                    )
                }
            }.onFailure(::fail)
    }

    private fun selectProfile(profileId: String) {
        if (currentState.profiles.none { it.id == profileId }) return
        cancelOperation()
        updateState {
            copy(
                selectedProfileId = profileId,
                schemas = emptyList(),
                connectionInfo = null,
                error = null,
            )
        }
    }

    private suspend fun saveProfile(
        profile: DatabaseConnectionProfile,
        credential: DatabaseCredential?,
    ) {
        val validationErrors = profile.validationErrors()
        if (validationErrors.isNotEmpty()) {
            updateState { copy(error = validationErrors.joinToString(". ")) }
            return
        }
        val profiles = currentState.profiles.filterNot { it.id == profile.id } + profile
        profileStore
            .save(profiles)
            .onSuccess {
                credential?.let { credentialStore.put(profile.id, it) }
                updateState {
                    copy(
                        profiles = profiles.sortedBy { it.name.lowercase() },
                        selectedProfileId = profile.id,
                        schemas = emptyList(),
                        connectionInfo = null,
                        error = null,
                    )
                }
            }.onFailure(::fail)
    }

    private suspend fun deleteProfile(profileId: String) {
        cancelOperation()
        val profiles = currentState.profiles.filterNot { it.id == profileId }
        profileStore
            .save(profiles)
            .onSuccess {
                credentialStore.remove(profileId)
                updateState {
                    copy(
                        profiles = profiles,
                        selectedProfileId = selectedProfileId.takeUnless { it == profileId },
                        schemas = if (selectedProfileId == profileId) emptyList() else schemas,
                        connectionInfo = if (selectedProfileId == profileId) null else connectionInfo,
                        error = null,
                    )
                }
            }.onFailure(::fail)
    }

    private fun testConnection() {
        val profile = selectedProfileOrFail() ?: return
        startOperation(DatabaseOperation.TESTING) {
            val info = executionService.testConnection(profile, credentialFor(profile.id))
            updateState { copy(connectionInfo = info) }
            emitEffect(DatabaseEffect.ShowMessage("Connected to ${info.productName} ${info.productVersion}"))
        }
    }

    private fun refreshSchema() {
        val profile = selectedProfileOrFail() ?: return
        startOperation(DatabaseOperation.INTROSPECTING) {
            val schemas = executionService.introspect(profile, credentialFor(profile.id))
            updateState { copy(schemas = schemas) }
            emitEffect(DatabaseEffect.ShowMessage("Loaded ${schemas.sumOf { it.tables.size }} database objects"))
        }
    }

    private suspend fun executeQuery() {
        val profile = selectedProfileOrFail() ?: return
        if (currentState.query.isBlank()) {
            updateState { copy(error = "Enter a SQL statement to execute") }
            return
        }
        emitEffect(DatabaseEffect.OpenQueryResults)
        startOperation(DatabaseOperation.EXECUTING) {
            val result = executionService.execute(profile, credentialFor(profile.id), currentState.query)
            updateState { copy(result = result) }
        }
    }

    private fun startOperation(
        operation: DatabaseOperation,
        block: suspend () -> Unit,
    ) {
        cancelOperation()
        val generation = operationGeneration
        updateState { copy(operation = operation, error = null) }
        operationJob =
            viewModelScope.launch {
                try {
                    block()
                } catch (_: CancellationException) {
                    // Cancellation is reflected by the IDLE state below.
                } catch (error: Exception) {
                    fail(error)
                } finally {
                    if (operationGeneration == generation) {
                        updateState { copy(operation = DatabaseOperation.IDLE) }
                    }
                }
            }
    }

    private fun cancelOperation() {
        operationJob?.cancel()
        operationJob = null
        operationGeneration += 1L
        executionService.cancel()
        updateState { copy(operation = DatabaseOperation.IDLE) }
    }

    private fun selectedProfileOrFail(): DatabaseConnectionProfile? =
        currentState.selectedProfile.also { profile ->
            if (profile == null) updateState { copy(error = "Select a database connection first") }
        }

    private fun credentialFor(profileId: String): DatabaseCredential =
        credentialStore.get(profileId) ?: DatabaseCredential.Empty

    private fun fail(error: Throwable) {
        updateState {
            copy(
                operation = DatabaseOperation.IDLE,
                error = error.message?.takeIf { it.isNotBlank() } ?: "Database operation failed",
                isLoadingProfiles = false,
            )
        }
    }
}
