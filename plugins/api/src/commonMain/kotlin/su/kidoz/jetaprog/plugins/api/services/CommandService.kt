package su.kidoz.jetaprog.plugins.api.services

import su.kidoz.jetaprog.common.Disposable

/**
 * Service for registering and executing commands.
 */
public interface CommandService {
    /**
     * Registers a command handler.
     * @param id The command ID (e.g., "myPlugin.doSomething")
     * @param handler The command handler
     * @return Disposable to unregister the command
     */
    public fun registerCommand(
        id: String,
        handler: suspend (args: List<Any?>) -> Any?,
    ): Disposable

    /**
     * Registers a text editor command handler.
     * @param id The command ID
     * @param handler The command handler with access to the active editor
     * @return Disposable to unregister the command
     */
    public fun registerTextEditorCommand(
        id: String,
        handler: suspend (editor: TextEditor, edit: TextEditorEdit, args: List<Any?>) -> Unit,
    ): Disposable

    /**
     * Executes a command.
     * @param id The command ID
     * @param args Arguments to pass to the command
     * @return The result of the command execution
     */
    public suspend fun executeCommand(
        id: String,
        vararg args: Any?,
    ): Any?

    /**
     * Gets all registered command IDs.
     * @param filterInternal Whether to filter out internal commands
     * @return List of command IDs
     */
    public fun getCommands(filterInternal: Boolean = true): List<String>

    /**
     * Checks if a command is registered.
     * @param id The command ID
     * @return Whether the command is registered
     */
    public fun hasCommand(id: String): Boolean
}
