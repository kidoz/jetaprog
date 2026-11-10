package su.kidoz.jetaprog.acp.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Distinguishes an omitted patch field from a value, including explicit null. */
@Serializable(with = AcpPatchSerializer::class)
public sealed interface AcpPatch<out T> {
    /** Retain the stored value. */
    public data object Absent : AcpPatch<Nothing>

    /** Replace the stored value; null clears it. */
    public data class Value<T>(
        public val value: T?,
    ) : AcpPatch<T>
}

/** Serializes patch values as ordinary nullable JSON fields. Absent fields must use a default. */
public class AcpPatchSerializer<T : Any>(
    serializer: KSerializer<T>,
) : KSerializer<AcpPatch<T>> {
    private val nullableSerializer = serializer.nullable
    override val descriptor: SerialDescriptor = nullableSerializer.descriptor

    override fun deserialize(decoder: Decoder): AcpPatch<T> =
        AcpPatch.Value(decoder.decodeSerializableValue(nullableSerializer))

    override fun serialize(
        encoder: Encoder,
        value: AcpPatch<T>,
    ) {
        require(value is AcpPatch.Value) { "An absent patch must be omitted" }
        encoder.encodeSerializableValue(nullableSerializer, value.value)
    }
}
