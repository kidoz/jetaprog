package su.kidoz.jetaprog.plugins.runtime.services

import su.kidoz.jetaprog.common.Disposable
import su.kidoz.jetaprog.plugins.api.services.CommandService
import su.kidoz.jetaprog.plugins.api.services.TextEditor
import su.kidoz.jetaprog.plugins.api.services.TextEditorEdit
import su.kidoz.jetaprog.plugins.runtime.activation.ActivationEventService
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of CommandService for registering and executing commands.
 *
 * @param activationEvents Service for firing activation triggers when commands are invoked.
 * @param getActiveEditor Factory for getting the active text editor.
 */
public class CommandServiceImpl(
    private val activationEvents: ActivationEventService? = null,
    private val getActiveEditor: () -> TextEditor? = { null },
) : CommandService {
    private val commands = ConcurrentHashMap<String, suspend (args: List<Any?>) -> Any?>()
    private val textEditorCommands =
        ConcurrentHashMap<String, suspend (editor: TextEditor, edit: TextEditorEdit, args: List<Any?>) -> Unit>()

    override fun registerCommand(
        id: String,
        handler: suspend (args: List<Any?>) -> Any?,
    ): Disposable {
        commands[id] = handler
        return Disposable { commands.remove(id) }
    }

    override fun registerTextEditorCommand(
        id: String,
        handler: suspend (editor: TextEditor, edit: TextEditorEdit, args: List<Any?>) -> Unit,
    ): Disposable {
        textEditorCommands[id] = handler
        return Disposable { textEditorCommands.remove(id) }
    }

    override suspend fun executeCommand(
        id: String,
        vararg args: Any?,
    ): Any? {
        // Fire command invoked trigger (may activate pending plugins)
        activationEvents?.fireCommandInvoked(id)

        val handler = commands[id]
        if (handler != null) {
            return handler(args.toList())
        }

        // Text editor commands require an active editor
        val textHandler = textEditorCommands[id]
        if (textHandler != null) {
            val editor =
                getActiveEditor()
                    ?: throw IllegalStateException("No active editor for text editor command: $id")

            // Create a basic edit builder
            val edit =
                object : TextEditorEdit {
                    override fun insert(
                        position: su.kidoz.jetaprog.common.text.TextPosition,
                        text: String,
                    ) {
                        // Would be implemented by actual editor
                    }

                    override fun delete(range: su.kidoz.jetaprog.common.text.TextRange) {
                        // Would be implemented by actual editor
                    }

                    override fun replace(
                        range: su.kidoz.jetaprog.common.text.TextRange,
                        text: String,
                    ) {
                        // Would be implemented by actual editor
                    }
                }

            textHandler(editor, edit, args.toList())
            return null
        }

        throw NoSuchElementException("Command not found: $id")
    }

    override fun getCommands(filterInternal: Boolean): List<String> {
        val allCommands = commands.keys + textEditorCommands.keys
        return if (filterInternal) {
            allCommands.filter { !it.startsWith("_") }
        } else {
            allCommands.toList()
        }
    }

    override fun hasCommand(id: String): Boolean = commands.containsKey(id) || textEditorCommands.containsKey(id)
}
