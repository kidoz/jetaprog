package su.kidoz.jetaprog.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.common.Disposable
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/** Persistence contract for password-free database connection profiles. */
public interface DatabaseProfileStore {
    /** Loads all saved profiles. */
    public suspend fun load(): Result<List<DatabaseConnectionProfile>>

    /** Replaces the saved profiles with [profiles]. */
    public suspend fun save(profiles: List<DatabaseConnectionProfile>): Result<Unit>
}

/** JSON implementation storing connection profiles in the IDE configuration directory. */
public class JvmDatabaseProfileStore(
    private val file: File,
) : DatabaseProfileStore {
    private val mutex = Mutex()
    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    override suspend fun load(): Result<List<DatabaseConnectionProfile>> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!file.exists()) {
                        emptyList()
                    } else {
                        json.decodeFromString<DatabaseProfilesFile>(file.readText()).profiles
                    }
                }
            }
        }

    override suspend fun save(profiles: List<DatabaseConnectionProfile>): Result<Unit> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    file.parentFile?.mkdirs()
                    val temporaryFile = File(file.parentFile, "${file.name}.tmp")
                    temporaryFile.writeText(json.encodeToString(DatabaseProfilesFile(profiles)))
                    try {
                        Files.move(
                            temporaryFile.toPath(),
                            file.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(
                            temporaryFile.toPath(),
                            file.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                    Unit
                }
            }
        }
}

/** In-memory credential storage cleared when the application session ends. */
public class SessionDatabaseCredentialStore : Disposable {
    private val credentials = ConcurrentHashMap<String, DatabaseCredential>()

    /** Associates [credential] with [profileId] for this process only. */
    public fun put(
        profileId: String,
        credential: DatabaseCredential,
    ) {
        credentials[profileId] = credential
    }

    /** Returns the session credential for [profileId], if one was supplied. */
    public fun get(profileId: String): DatabaseCredential? = credentials[profileId]

    /** Removes the session credential for [profileId]. */
    public fun remove(profileId: String) {
        credentials.remove(profileId)
    }

    override fun dispose() {
        credentials.clear()
    }
}

@Serializable
private data class DatabaseProfilesFile(
    val profiles: List<DatabaseConnectionProfile> = emptyList(),
)
