package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/*
 * The specification declares several completion, hover and signature fields as unions.
 * The models here keep the richest arm and these serializers fold the other arms into it.
 * Without them one unexpected arm made the whole response fail to decode, and the failure
 * was logged once and shown to the user as "No completions".
 */

/**
 * Reads `string | MarkupContent`. Servers such as pyright and clangd send plain strings
 * for documentation; a string is treated as plaintext.
 */
internal object MarkupContentOrStringSerializer : KSerializer<MarkupContent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MarkupContentOrString")

    override fun deserialize(decoder: Decoder): MarkupContent {
        val input = decoder as? JsonDecoder ?: error("MarkupContent is only decoded from JSON")
        return input.decodeJsonElement().toMarkupContent(defaultKind = "plaintext")
    }

    override fun serialize(
        encoder: Encoder,
        value: MarkupContent,
    ) {
        val output = encoder as? JsonEncoder ?: error("MarkupContent is only encoded to JSON")
        output.encodeJsonElement(output.json.encodeToJsonElement(MarkupContent.serializer(), value))
    }
}

/**
 * Reads hover `contents`, declared as `MarkupContent | MarkedString | MarkedString[]` where a
 * `MarkedString` is either markdown text or `{ language, value }`. Everything collapses to a
 * single markdown [MarkupContent]; code marked strings become fenced blocks.
 */
internal object HoverContentsSerializer : KSerializer<MarkupContent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("HoverContents")

    override fun deserialize(decoder: Decoder): MarkupContent {
        val input = decoder as? JsonDecoder ?: error("Hover contents are only decoded from JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonArray -> {
                MarkupContent(
                    kind = "markdown",
                    value =
                        element
                            .map { it.toMarkupContent(defaultKind = "markdown").value }
                            .filter { it.isNotBlank() }
                            .joinToString(separator = "\n\n"),
                )
            }

            else -> {
                element.toMarkupContent(defaultKind = "markdown")
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: MarkupContent,
    ) {
        val output = encoder as? JsonEncoder ?: error("Hover contents are only encoded to JSON")
        output.encodeJsonElement(output.json.encodeToJsonElement(MarkupContent.serializer(), value))
    }
}

private fun JsonElement.toMarkupContent(defaultKind: String): MarkupContent =
    when (this) {
        is JsonPrimitive -> {
            MarkupContent(kind = defaultKind, value = contentOrNull.orEmpty())
        }

        is JsonObject -> {
            val value = this["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val language = this["language"]?.jsonPrimitive?.contentOrNull
            when {
                // MarkedString code form: render as a fenced block so the language survives.
                language != null -> MarkupContent(kind = "markdown", value = "```$language\n$value\n```")

                else -> MarkupContent(kind = this["kind"]?.jsonPrimitive?.contentOrNull ?: defaultKind, value = value)
            }
        }

        else -> {
            MarkupContent(kind = defaultKind, value = "")
        }
    }

/**
 * The edit a completion item asks for, declared as `TextEdit | InsertReplaceEdit`.
 *
 * A plain `TextEdit` covers the same span whichever way the item is accepted. An
 * `InsertReplaceEdit` distinguishes accepting with the caret's suffix kept ([insert])
 * from overwriting the rest of the word ([replace]).
 */
@Serializable(with = CompletionTextEditSerializer::class)
public data class LspCompletionTextEdit(
    val newText: String,
    val insert: LspRange,
    val replace: LspRange = insert,
)

internal object CompletionTextEditSerializer : KSerializer<LspCompletionTextEdit> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CompletionTextEdit")

    override fun deserialize(decoder: Decoder): LspCompletionTextEdit {
        val input = decoder as? JsonDecoder ?: error("Completion edits are only decoded from JSON")
        val element = input.decodeJsonElement() as? JsonObject ?: error("Completion edit must be an object")
        val newText = element["newText"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val range = element["range"]?.let { input.json.decodeFromJsonElement(LspRange.serializer(), it) }
        if (range != null) return LspCompletionTextEdit(newText = newText, insert = range, replace = range)
        val insert = element["insert"] ?: error("Completion edit has neither range nor insert")
        val replace = element["replace"] ?: insert
        return LspCompletionTextEdit(
            newText = newText,
            insert = input.json.decodeFromJsonElement(LspRange.serializer(), insert),
            replace = input.json.decodeFromJsonElement(LspRange.serializer(), replace),
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: LspCompletionTextEdit,
    ) {
        val output = encoder as? JsonEncoder ?: error("Completion edits are only encoded to JSON")
        val json = output.json
        output.encodeJsonElement(
            if (value.insert == value.replace) {
                buildJsonObject {
                    put("newText", value.newText)
                    put("range", json.encodeToJsonElement(LspRange.serializer(), value.insert))
                }
            } else {
                buildJsonObject {
                    put("newText", value.newText)
                    put("insert", json.encodeToJsonElement(LspRange.serializer(), value.insert))
                    put("replace", json.encodeToJsonElement(LspRange.serializer(), value.replace))
                }
            },
        )
    }
}

/**
 * A signature parameter's label, declared as `string | [uinteger, uinteger]`.
 *
 * clangd and rust-analyzer send the offset pair, which addresses a substring of the
 * enclosing signature label; [text] is null in that case and [start]/[end] are set.
 */
@Serializable(with = ParameterLabelSerializer::class)
public data class LspParameterLabel(
    val text: String? = null,
    val start: Int? = null,
    val end: Int? = null,
) {
    /** Resolves the label text against the signature it belongs to. */
    public fun resolve(signatureLabel: String): String {
        text?.let { return it }
        val from = start ?: return ""
        val to = end ?: return ""
        if (from < 0 || to > signatureLabel.length || from > to) return ""
        return signatureLabel.substring(from, to)
    }
}

internal object ParameterLabelSerializer : KSerializer<LspParameterLabel> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ParameterLabel")

    override fun deserialize(decoder: Decoder): LspParameterLabel {
        val input = decoder as? JsonDecoder ?: error("Parameter labels are only decoded from JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonArray -> {
                LspParameterLabel(
                    start = element.getOrNull(0)?.jsonPrimitive?.intOrNull,
                    end = element.getOrNull(1)?.jsonPrimitive?.intOrNull,
                )
            }

            is JsonPrimitive -> {
                LspParameterLabel(text = element.contentOrNull.orEmpty())
            }

            else -> {
                LspParameterLabel(text = "")
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: LspParameterLabel,
    ) {
        val output = encoder as? JsonEncoder ?: error("Parameter labels are only encoded to JSON")
        val text = value.text
        output.encodeJsonElement(
            if (text != null) {
                JsonPrimitive(text)
            } else {
                JsonArray(listOf(JsonPrimitive(value.start ?: 0), JsonPrimitive(value.end ?: 0)))
            },
        )
    }
}

/**
 * Reads a `textDocument/completion` result, declared as `CompletionItem[] | CompletionList`.
 * The bare array form (gopls, older servers) is a complete list.
 */
public object LspCompletionResultSerializer : KSerializer<LspCompletionList> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CompletionResult")

    override fun deserialize(decoder: Decoder): LspCompletionList {
        val input = decoder as? JsonDecoder ?: error("Completion results are only decoded from JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonArray -> {
                LspCompletionList(
                    isIncomplete = false,
                    items = input.json.decodeFromJsonElement(ListSerializer(LspCompletionItem.serializer()), element),
                )
            }

            else -> {
                input.json.decodeFromJsonElement(LspCompletionList.serializer(), element)
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: LspCompletionList,
    ) {
        val output = encoder as? JsonEncoder ?: error("Completion results are only encoded to JSON")
        output.encodeJsonElement(output.json.encodeToJsonElement(LspCompletionList.serializer(), value))
    }
}

/**
 * Reads and writes [LspCompletionItemKind] as the number the protocol uses. The enum's
 * generated serializer names each value by its number but writes it as a JSON string,
 * which a strict server rejects when an item is sent back for `completionItem/resolve`.
 * Unknown numbers (a newer specification) fall back to [LspCompletionItemKind.Text].
 */
internal object CompletionItemKindSerializer : KSerializer<LspCompletionItemKind> {
    private val generated = LspCompletionItemKind.serializer()

    override val descriptor: SerialDescriptor = generated.descriptor

    override fun deserialize(decoder: Decoder): LspCompletionItemKind {
        val input = decoder as? JsonDecoder ?: return generated.deserialize(decoder)
        val name = (input.decodeJsonElement() as? JsonPrimitive)?.contentOrNull ?: return LspCompletionItemKind.Text
        val index = generated.descriptor.getElementIndex(name)
        return LspCompletionItemKind.entries.getOrNull(index) ?: LspCompletionItemKind.Text
    }

    override fun serialize(
        encoder: Encoder,
        value: LspCompletionItemKind,
    ) {
        val output = encoder as? JsonEncoder ?: return generated.serialize(encoder, value)
        val number = generated.descriptor.getElementName(value.ordinal).toIntOrNull()
        output.encodeJsonElement(if (number != null) JsonPrimitive(number) else JsonPrimitive(value.name))
    }
}
