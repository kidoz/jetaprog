package su.kidoz.jetaprog.app.debug

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import su.kidoz.jetaprog.app.ui.debug.BreakpointView
import su.kidoz.jetaprog.platform.filesystem.FileSystem

/**
 * The persisted debugger preferences for a project.
 *
 * @property breakpoints User breakpoints.
 * @property watches Watch expressions.
 * @property showInlineValues Whether inline value hints are enabled.
 */
public data class DebugPrefs(
    val breakpoints: List<BreakpointView>,
    val watches: List<String>,
    val showInlineValues: Boolean,
)

@Serializable
private data class BreakpointDto(
    val file: String,
    val line: Int,
    val enabled: Boolean = true,
    val condition: String? = null,
)

@Serializable
private data class DebugPrefsDto(
    val breakpoints: List<BreakpointDto> = emptyList(),
    val watches: List<String> = emptyList(),
    val showInlineValues: Boolean = true,
)

/**
 * Best-effort persistence of debugger preferences in `.jetaprog/debug-prefs.json`
 * under the project root. Failures are swallowed.
 *
 * @param fileSystem The file system used to read and write the prefs file.
 * @param projectPath The project root.
 */
public class DebugPrefsStore(
    private val fileSystem: FileSystem,
    private val projectPath: String,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    private val dir = "$projectPath/.jetaprog"
    private val file = "$dir/debug-prefs.json"

    /** Loads preferences, falling back to defaults when absent or unreadable. */
    public suspend fun load(): DebugPrefs {
        val dto =
            runCatching {
                if (!fileSystem.exists(file)) return@runCatching DebugPrefsDto()
                json.decodeFromString(DebugPrefsDto.serializer(), fileSystem.readText(file).getOrThrow())
            }.getOrDefault(DebugPrefsDto())
        return DebugPrefs(
            breakpoints =
                dto.breakpoints.map {
                    BreakpointView(it.file, it.line, it.enabled, verified = false, condition = it.condition)
                },
            watches = dto.watches,
            showInlineValues = dto.showInlineValues,
        )
    }

    /** Persists [prefs], ignoring any I/O failure. */
    public suspend fun save(prefs: DebugPrefs) {
        runCatching {
            if (!fileSystem.exists(dir)) fileSystem.createDirectory(dir)
            val dto =
                DebugPrefsDto(
                    breakpoints =
                        prefs.breakpoints.map { BreakpointDto(it.file, it.line, it.enabled, it.condition) },
                    watches = prefs.watches,
                    showInlineValues = prefs.showInlineValues,
                )
            fileSystem.writeText(file, json.encodeToString(DebugPrefsDto.serializer(), dto))
        }
    }
}
