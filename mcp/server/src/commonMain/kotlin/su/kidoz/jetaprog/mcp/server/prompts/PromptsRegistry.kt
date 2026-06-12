package su.kidoz.jetaprog.mcp.server.prompts

import kotlinx.serialization.json.JsonObject

/**
 * A prompt template that can be used by AI agents.
 */
public data class Prompt(
    /**
     * The prompt name.
     */
    val name: String,
    /**
     * Description of what the prompt does.
     */
    val description: String,
    /**
     * Arguments the prompt accepts.
     */
    val arguments: List<PromptArgument> = emptyList(),
    /**
     * Handler that generates the prompt text.
     */
    val handler: suspend (args: JsonObject) -> String,
)

/**
 * An argument for a prompt.
 */
public data class PromptArgument(
    /**
     * The argument name.
     */
    val name: String,
    /**
     * Description of the argument.
     */
    val description: String,
    /**
     * Whether the argument is required.
     */
    val required: Boolean = false,
)

/**
 * Registry for MCP prompts.
 */
public class PromptsRegistry {
    private val prompts = mutableMapOf<String, Prompt>()

    /**
     * Registers a prompt.
     */
    public fun register(prompt: Prompt) {
        prompts[prompt.name] = prompt
    }

    /**
     * Unregisters a prompt.
     */
    public fun unregister(name: String) {
        prompts.remove(name)
    }

    /**
     * Gets a prompt by name.
     */
    public fun get(name: String): Prompt? = prompts[name]

    /**
     * Lists all registered prompts.
     */
    public fun list(): List<Prompt> = prompts.values.toList()

    /**
     * Generates a prompt.
     */
    public suspend fun generate(
        name: String,
        args: JsonObject,
    ): Result<String> {
        val prompt =
            prompts[name] ?: return Result.failure(
                IllegalArgumentException("Prompt not found: $name"),
            )
        return try {
            Result.success(prompt.handler(args))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
