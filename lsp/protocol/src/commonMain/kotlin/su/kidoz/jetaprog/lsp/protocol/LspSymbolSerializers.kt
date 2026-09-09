package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

/*
 * Symbol responses are unions too: `textDocument/documentSymbol` answers with either
 * hierarchical `DocumentSymbol[]` or flat `SymbolInformation[]`, and `workspace/symbol`
 * with `SymbolInformation[]` or `WorkspaceSymbol[]`, whose location may carry only a
 * URI. Each form is folded into the model navigation already consumes.
 */

/**
 * Reads a `textDocument/documentSymbol` result. Flat `SymbolInformation` entries become
 * childless [LspDocumentSymbol]s spanning their location.
 */
public object LspDocumentSymbolResultSerializer : KSerializer<List<LspDocumentSymbol>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DocumentSymbolResult")

    override fun deserialize(decoder: Decoder): List<LspDocumentSymbol> {
        val input = decoder as? JsonDecoder ?: error("Document symbols are only decoded from JSON")
        val elements = input.decodeJsonElement() as? JsonArray ?: return emptyList()
        return elements.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            if (obj.containsKey("range")) {
                runCatching { input.json.decodeFromJsonElement(LspDocumentSymbol.serializer(), obj) }.getOrNull()
            } else {
                obj.toSymbolInformation(input.json)?.let { info ->
                    LspDocumentSymbol(
                        name = info.name,
                        detail = info.containerName,
                        kind = info.kind,
                        deprecated = info.deprecated,
                        range = info.location.range,
                        selectionRange = info.location.range,
                    )
                }
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: List<LspDocumentSymbol>,
    ) {
        val output = encoder as? JsonEncoder ?: error("Document symbols are only encoded to JSON")
        output.encodeJsonElement(output.json.encodeToJsonElement(ListSerializer(LspDocumentSymbol.serializer()), value))
    }
}

/**
 * Reads a `workspace/symbol` result. A `WorkspaceSymbol` whose location has no range
 * (servers may resolve it lazily) is placed at the start of its file.
 */
public object LspWorkspaceSymbolResultSerializer : KSerializer<List<LspSymbolInformation>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("WorkspaceSymbolResult")

    override fun deserialize(decoder: Decoder): List<LspSymbolInformation> {
        val input = decoder as? JsonDecoder ?: error("Workspace symbols are only decoded from JSON")
        val elements = input.decodeJsonElement() as? JsonArray ?: return emptyList()
        return elements.mapNotNull { element -> (element as? JsonObject)?.toSymbolInformation(input.json) }
    }

    override fun serialize(
        encoder: Encoder,
        value: List<LspSymbolInformation>,
    ) {
        val output = encoder as? JsonEncoder ?: error("Workspace symbols are only encoded to JSON")
        output.encodeJsonElement(
            output.json.encodeToJsonElement(ListSerializer(LspSymbolInformation.serializer()), value),
        )
    }
}

private fun JsonObject.toSymbolInformation(json: kotlinx.serialization.json.Json): LspSymbolInformation? {
    runCatching { json.decodeFromJsonElement(LspSymbolInformation.serializer(), this) }.getOrNull()?.let { return it }
    val name = this["name"]?.jsonPrimitive?.contentOrNull ?: return null
    val kind =
        this["kind"]?.let {
            runCatching {
                json.decodeFromJsonElement(
                    LspSymbolKind.serializer(),
                    it,
                )
            }.getOrNull()
        }
    val location = this["location"] as? JsonObject ?: return null
    val uri = location["uri"]?.jsonPrimitive?.contentOrNull ?: return null
    val range =
        location["range"]?.let { runCatching { json.decodeFromJsonElement(LspRange.serializer(), it) }.getOrNull() }
            ?: LspRange(LspPosition(0, 0), LspPosition(0, 0))
    return LspSymbolInformation(
        name = name,
        kind = kind ?: LspSymbolKind.Object,
        location = LspLocation(uri, range),
        containerName = this["containerName"]?.jsonPrimitive?.contentOrNull,
    )
}

/**
 * Reads a definition-family result, declared as `Location | Location[] | LocationLink[]`.
 * Servers that see `linkSupport` (rust-analyzer, clangd) answer with links, whose target
 * selection range is where the caret should land.
 */
public object LspLocationsResultSerializer : KSerializer<List<LspLocation>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LocationsResult")

    override fun deserialize(decoder: Decoder): List<LspLocation> {
        val input = decoder as? JsonDecoder ?: error("Locations are only decoded from JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonArray -> element.mapNotNull { (it as? JsonObject)?.toLocation(input.json) }
            is JsonObject -> listOfNotNull(element.toLocation(input.json))
            else -> emptyList()
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: List<LspLocation>,
    ) {
        val output = encoder as? JsonEncoder ?: error("Locations are only encoded to JSON")
        output.encodeJsonElement(output.json.encodeToJsonElement(ListSerializer(LspLocation.serializer()), value))
    }

    private fun JsonObject.toLocation(json: kotlinx.serialization.json.Json): LspLocation? {
        if (containsKey(
                "uri",
            )
        ) {
            return runCatching { json.decodeFromJsonElement(LspLocation.serializer(), this) }.getOrNull()
        }
        val targetUri = this["targetUri"]?.jsonPrimitive?.contentOrNull ?: return null
        val range = (this["targetSelectionRange"] ?: this["targetRange"]) ?: return null
        return runCatching {
            LspLocation(
                targetUri,
                json.decodeFromJsonElement(LspRange.serializer(), range),
            )
        }.getOrNull()
    }
}
