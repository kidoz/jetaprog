package su.kidoz.jetaprog.mcp.bridge.adapters

/**
 * Adapter for file system operations exposed via MCP.
 */
public interface FileSystemAdapter {
    /**
     * Reads a file's contents.
     */
    public suspend fun readFile(path: String): Result<String>

    /**
     * Writes content to a file.
     */
    public suspend fun writeFile(
        path: String,
        content: String,
    ): Result<Unit>

    /**
     * Lists directory contents.
     */
    public suspend fun listDirectory(path: String): Result<List<FileInfo>>

    /**
     * Checks if a path exists.
     */
    public suspend fun exists(path: String): Boolean
}

/**
 * File information.
 */
public data class FileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
)

/**
 * Adapter for editor operations exposed via MCP.
 */
public interface EditorAdapter {
    /**
     * Gets the currently open files.
     */
    public suspend fun getOpenFiles(): List<OpenFileInfo>

    /**
     * Gets the active file.
     */
    public suspend fun getActiveFile(): OpenFileInfo?

    /**
     * Opens a file in the editor.
     */
    public suspend fun openFile(path: String): Result<Unit>
}

/**
 * Information about an open file.
 */
public data class OpenFileInfo(
    val path: String,
    val languageId: String,
    val isDirty: Boolean,
)

/**
 * Adapter for build system operations exposed via MCP.
 */
public interface BuildSystemAdapter {
    /**
     * Runs a build task.
     */
    public suspend fun runTask(
        task: String,
        args: List<String> = emptyList(),
    ): Result<BuildResult>

    /**
     * Gets the build configuration.
     */
    public suspend fun getConfiguration(): BuildConfiguration
}

/**
 * Result of a build operation.
 */
public data class BuildResult(
    val success: Boolean,
    val output: String,
    val exitCode: Int,
)

/**
 * Build configuration.
 */
public data class BuildConfiguration(
    val buildSystem: String,
    val projectName: String,
    val tasks: List<String>,
)

/**
 * Adapter for diagnostics exposed via MCP.
 */
public interface DiagnosticsAdapter {
    /**
     * Gets diagnostics for a file or the whole project.
     */
    public suspend fun getDiagnostics(path: String? = null): List<DiagnosticInfo>

    /**
     * Gets a summary of all diagnostics.
     */
    public suspend fun getSummary(): DiagnosticsSummary
}

/**
 * Diagnostic information.
 */
public data class DiagnosticInfo(
    val path: String,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: String,
    val source: String?,
)

/**
 * Summary of diagnostics.
 */
public data class DiagnosticsSummary(
    val errors: Int,
    val warnings: Int,
    val info: Int,
)

/**
 * Adapter for terminal operations exposed via MCP.
 */
public interface TerminalAdapter {
    /**
     * Executes a command in the terminal.
     */
    public suspend fun execute(
        command: String,
        cwd: String? = null,
        timeout: Long = 30000,
    ): Result<CommandResult>
}

/**
 * Result of a command execution.
 */
public data class CommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)

/**
 * Adapter for code navigation exposed via MCP.
 */
public interface NavigationAdapter {
    /**
     * Finds the definition of a symbol.
     */
    public suspend fun findDefinition(
        symbol: String,
        file: String?,
    ): List<LocationInfo>

    /**
     * Finds references to a symbol.
     */
    public suspend fun findReferences(
        symbol: String,
        file: String?,
    ): List<LocationInfo>
}

/**
 * Location information.
 */
public data class LocationInfo(
    val path: String,
    val line: Int,
    val column: Int,
)

/**
 * Adapter for refactoring operations exposed via MCP.
 */
public interface RefactoringAdapter {
    /**
     * Renames a symbol.
     */
    public suspend fun rename(
        file: String,
        line: Int,
        column: Int,
        newName: String,
    ): Result<RefactoringResult>
}

/**
 * Result of a refactoring operation.
 */
public data class RefactoringResult(
    val success: Boolean,
    val filesChanged: Int,
    val message: String?,
)

/**
 * Adapter for search operations exposed via MCP.
 */
public interface SearchAdapter {
    /**
     * Searches the codebase.
     */
    public suspend fun search(
        query: String,
        type: String = "text",
        filePattern: String? = null,
        maxResults: Int = 100,
    ): List<SearchResult>
}

/**
 * Search result.
 */
public data class SearchResult(
    val path: String,
    val line: Int,
    val column: Int,
    val text: String,
    val matchType: String,
)

/**
 * Adapter for version control operations exposed via MCP.
 */
public interface VcsAdapter {
    /**
     * Gets the current git status.
     */
    public suspend fun getStatus(): VcsStatus

    /**
     * Gets the diff.
     */
    public suspend fun getDiff(
        staged: Boolean = false,
        file: String? = null,
    ): String
}

/**
 * VCS status.
 */
public data class VcsStatus(
    val branch: String,
    val ahead: Int,
    val behind: Int,
    val staged: List<String>,
    val unstaged: List<String>,
    val untracked: List<String>,
)

/**
 * Adapter for workspace operations exposed via MCP.
 */
public interface WorkspaceAdapter {
    /**
     * Gets the project structure.
     */
    public suspend fun getProjectStructure(): ProjectStructure
}

/**
 * Project structure.
 */
public data class ProjectStructure(
    val name: String,
    val rootPath: String,
    val files: List<String>,
    val directories: List<String>,
)
