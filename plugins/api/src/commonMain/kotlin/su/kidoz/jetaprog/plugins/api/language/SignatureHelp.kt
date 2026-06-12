package su.kidoz.jetaprog.plugins.api.language

import kotlinx.serialization.Serializable

/**
 * Signature help information for function/method calls.
 */
@Serializable
public data class SignatureHelp(
    /**
     * One or more signatures.
     */
    val signatures: List<SignatureInformation>,
    /**
     * The active signature (0-indexed).
     */
    val activeSignature: Int = 0,
    /**
     * The active parameter of the active signature (0-indexed).
     */
    val activeParameter: Int = 0,
)

/**
 * Information about a single signature.
 */
@Serializable
public data class SignatureInformation(
    /**
     * The label of this signature (e.g., function name with parameters).
     */
    val label: String,
    /**
     * Documentation for this signature.
     */
    val documentation: String? = null,
    /**
     * The parameters of this signature.
     */
    val parameters: List<ParameterInformation> = emptyList(),
)

/**
 * Information about a single parameter.
 */
@Serializable
public data class ParameterInformation(
    /**
     * The label of this parameter (name).
     */
    val label: String,
    /**
     * Documentation for this parameter.
     */
    val documentation: String? = null,
)
