package su.kidoz.jetaprog.mcp.bridge

import su.kidoz.jetaprog.mcp.bridge.adapters.BuildSystemAdapter
import su.kidoz.jetaprog.mcp.bridge.adapters.DiagnosticsAdapter
import su.kidoz.jetaprog.mcp.bridge.adapters.EditorAdapter
import su.kidoz.jetaprog.mcp.bridge.adapters.FileSystemAdapter
import su.kidoz.jetaprog.mcp.bridge.adapters.NavigationAdapter
import su.kidoz.jetaprog.mcp.bridge.adapters.RefactoringAdapter
import su.kidoz.jetaprog.mcp.bridge.adapters.SearchAdapter
import su.kidoz.jetaprog.mcp.bridge.adapters.TerminalAdapter
import su.kidoz.jetaprog.mcp.bridge.adapters.VcsAdapter
import su.kidoz.jetaprog.mcp.bridge.adapters.WorkspaceAdapter

/**
 * Bridge between the MCP server and IDE services.
 *
 * Provides adapters that translate between MCP protocol and IDE functionality.
 */
public interface IdeServicesBridge {
    /**
     * File system operations adapter.
     */
    public val fileSystem: FileSystemAdapter

    /**
     * Editor operations adapter.
     */
    public val editor: EditorAdapter

    /**
     * Build system operations adapter.
     */
    public val buildSystem: BuildSystemAdapter

    /**
     * Diagnostics adapter.
     */
    public val diagnostics: DiagnosticsAdapter

    /**
     * Terminal operations adapter.
     */
    public val terminal: TerminalAdapter

    /**
     * Code navigation adapter.
     */
    public val navigation: NavigationAdapter

    /**
     * Refactoring operations adapter.
     */
    public val refactoring: RefactoringAdapter

    /**
     * Search operations adapter.
     */
    public val search: SearchAdapter

    /**
     * Version control operations adapter.
     */
    public val vcs: VcsAdapter

    /**
     * Workspace operations adapter.
     */
    public val workspace: WorkspaceAdapter
}
