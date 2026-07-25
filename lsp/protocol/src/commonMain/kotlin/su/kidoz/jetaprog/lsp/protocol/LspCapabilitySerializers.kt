package su.kidoz.jetaprog.lsp.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Reads a server capability that the LSP specification declares as `boolean | Options`.
 *
 * Real servers use both forms for the same field — clangd answers `documentFormattingProvider`
 * with `true` but `codeActionProvider` with `{"codeActionKinds": [...]}`. Modelling these as
 * plain booleans made the whole `initialize` response fail to parse, which silently prevented
 * the language server from starting. The option details are not used anywhere, so the object
 * form collapses to `true`: the server supports the feature.
 */
internal object LspCapabilitySerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LspCapability", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val input = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> element.booleanOrNull ?: true

            // Object or array form: advertised with options, so it is supported.
            else -> true
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: Boolean,
    ) {
        encoder.encodeBoolean(value)
    }
}

/**
 * Reads `TextDocumentSyncOptions.save`, which the specification declares as
 * `boolean | SaveOptions`. clangd sends the boolean form.
 */
internal object SaveOptionsSerializer : KSerializer<SaveOptions> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SaveOptionsOrBoolean")

    override fun deserialize(decoder: Decoder): SaveOptions {
        val input = decoder as? JsonDecoder ?: return SaveOptions()
        val element = input.decodeJsonElement()
        val obj = element as? JsonObject ?: return SaveOptions()
        return SaveOptions(includeText = obj["includeText"]?.jsonPrimitive?.booleanOrNull)
    }

    override fun serialize(
        encoder: Encoder,
        value: SaveOptions,
    ) {
        val output = encoder as? JsonEncoder ?: return
        output.encodeJsonElement(
            buildJsonObject {
                value.includeText?.let { put("includeText", it) }
            },
        )
    }
}

/**
 * Reads `ServerCapabilities.textDocumentSync`, which the specification declares as
 * `TextDocumentSyncOptions | TextDocumentSyncKind`, the latter being a plain number.
 */
internal object TextDocumentSyncSerializer : KSerializer<TextDocumentSyncOptions> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TextDocumentSyncOptionsOrKind")

    override fun deserialize(decoder: Decoder): TextDocumentSyncOptions {
        val input = decoder as? JsonDecoder ?: return TextDocumentSyncOptions()
        return when (val element = input.decodeJsonElement()) {
            is JsonObject -> input.json.decodeFromJsonElement(element)
            is JsonPrimitive -> TextDocumentSyncOptions(change = element.intOrNull)
            else -> TextDocumentSyncOptions()
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: TextDocumentSyncOptions,
    ) {
        val output = encoder as? JsonEncoder ?: return
        output.encodeJsonElement(output.json.encodeToJsonElement(value))
    }
}
