package su.kidoz.jetaprog.editor.completion.smart

import kotlinx.serialization.Serializable

/**
 * Represents type information for a symbol or expression.
 *
 * This is a simplified representation of types that can be used
 * for completion filtering without full type system integration.
 */
@Serializable
public data class TypeInfo(
    /**
     * The simple name of the type (e.g., "String", "Int", "List").
     */
    val name: String,
    /**
     * The fully qualified name (e.g., "kotlin.String", "java.util.List").
     */
    val qualifiedName: String = name,
    /**
     * Type parameters if generic (e.g., ["String"] for List<String>).
     */
    val typeParameters: List<TypeInfo> = emptyList(),
    /**
     * Whether this type is nullable (e.g., String?).
     */
    val isNullable: Boolean = false,
    /**
     * Whether this is a function type.
     */
    val isFunctionType: Boolean = false,
    /**
     * Parameter types for function types.
     */
    val parameterTypes: List<TypeInfo> = emptyList(),
    /**
     * Return type for function types.
     */
    val returnType: TypeInfo? = null,
) {
    /**
     * Check if this type is assignable to another type.
     *
     * This is a simplified compatibility check that handles:
     * - Exact matches
     * - Nullable to non-nullable
     * - Basic subtype relationships for common types
     */
    public fun isAssignableTo(target: TypeInfo): Boolean {
        // Exact match
        if (qualifiedName == target.qualifiedName) {
            // Check nullability
            if (target.isNullable || !isNullable) {
                return checkTypeParameters(target)
            }
        }

        // Any type accepts everything
        if (target.qualifiedName == "kotlin.Any" || target.qualifiedName == "java.lang.Object") {
            return true
        }

        // Unit is compatible with void
        if ((qualifiedName == "kotlin.Unit" && target.qualifiedName == "void") ||
            (qualifiedName == "void" && target.qualifiedName == "kotlin.Unit")
        ) {
            return true
        }

        // Check known type hierarchies
        return isKnownSubtype(target)
    }

    private fun checkTypeParameters(target: TypeInfo): Boolean {
        if (typeParameters.size != target.typeParameters.size) {
            // Allow raw types
            return target.typeParameters.isEmpty()
        }
        return typeParameters.zip(target.typeParameters).all { (a, b) ->
            a.isAssignableTo(b)
        }
    }

    private fun isKnownSubtype(target: TypeInfo): Boolean {
        val subtypeMap =
            mapOf(
                "kotlin.Int" to setOf("kotlin.Number", "kotlin.Comparable"),
                "kotlin.Long" to setOf("kotlin.Number", "kotlin.Comparable"),
                "kotlin.Float" to setOf("kotlin.Number", "kotlin.Comparable"),
                "kotlin.Double" to setOf("kotlin.Number", "kotlin.Comparable"),
                "kotlin.Short" to setOf("kotlin.Number", "kotlin.Comparable"),
                "kotlin.Byte" to setOf("kotlin.Number", "kotlin.Comparable"),
                "kotlin.String" to setOf("kotlin.CharSequence", "kotlin.Comparable"),
                "java.lang.Integer" to setOf("java.lang.Number", "java.lang.Comparable"),
                "java.lang.Long" to setOf("java.lang.Number", "java.lang.Comparable"),
                "java.lang.String" to setOf("java.lang.CharSequence", "java.lang.Comparable"),
                "kotlin.collections.List" to setOf("kotlin.collections.Collection", "kotlin.collections.Iterable"),
                "kotlin.collections.Set" to setOf("kotlin.collections.Collection", "kotlin.collections.Iterable"),
                "kotlin.collections.MutableList" to
                    setOf(
                        "kotlin.collections.List",
                        "kotlin.collections.MutableCollection",
                    ),
            )

        return subtypeMap[qualifiedName]?.contains(target.qualifiedName) == true
    }

    override fun toString(): String =
        buildString {
            append(name)
            if (typeParameters.isNotEmpty()) {
                append("<")
                append(typeParameters.joinToString(", "))
                append(">")
            }
            if (isNullable) append("?")
        }

    public companion object {
        /** Unknown type placeholder. */
        public val Unknown: TypeInfo = TypeInfo("Unknown", "unknown")

        /** Unit/void type. */
        public val Unit: TypeInfo = TypeInfo("Unit", "kotlin.Unit")

        /** Boolean type. */
        public val Boolean: TypeInfo = TypeInfo("Boolean", "kotlin.Boolean")

        /** Int type. */
        public val Int: TypeInfo = TypeInfo("Int", "kotlin.Int")

        /** Long type. */
        public val Long: TypeInfo = TypeInfo("Long", "kotlin.Long")

        /** String type. */
        public val String: TypeInfo = TypeInfo("String", "kotlin.String")

        /** Any type. */
        public val Any: TypeInfo = TypeInfo("Any", "kotlin.Any")

        /**
         * Parse a simple type string into TypeInfo.
         */
        public fun parse(typeString: String): TypeInfo {
            val trimmed = typeString.trim()
            val isNullable = trimmed.endsWith("?")
            val base = if (isNullable) trimmed.dropLast(1) else trimmed

            // Check for generic types
            val genericStart = base.indexOf('<')
            return if (genericStart > 0) {
                val name = base.substring(0, genericStart)
                val paramsStr = base.substring(genericStart + 1, base.length - 1)
                val params = parseTypeParameters(paramsStr)
                TypeInfo(
                    name = name,
                    qualifiedName = resolveQualifiedName(name),
                    typeParameters = params,
                    isNullable = isNullable,
                )
            } else {
                TypeInfo(
                    name = base,
                    qualifiedName = resolveQualifiedName(base),
                    isNullable = isNullable,
                )
            }
        }

        private fun parseTypeParameters(paramsStr: String): List<TypeInfo> {
            // Simple parsing - doesn't handle nested generics perfectly
            return paramsStr.split(',').map { parse(it.trim()) }
        }

        private fun resolveQualifiedName(simpleName: String): String =
            when (simpleName) {
                "Int" -> "kotlin.Int"
                "Long" -> "kotlin.Long"
                "Short" -> "kotlin.Short"
                "Byte" -> "kotlin.Byte"
                "Float" -> "kotlin.Float"
                "Double" -> "kotlin.Double"
                "Boolean" -> "kotlin.Boolean"
                "Char" -> "kotlin.Char"
                "String" -> "kotlin.String"
                "Unit" -> "kotlin.Unit"
                "Any" -> "kotlin.Any"
                "Nothing" -> "kotlin.Nothing"
                "List" -> "kotlin.collections.List"
                "Set" -> "kotlin.collections.Set"
                "Map" -> "kotlin.collections.Map"
                "MutableList" -> "kotlin.collections.MutableList"
                "MutableSet" -> "kotlin.collections.MutableSet"
                "MutableMap" -> "kotlin.collections.MutableMap"
                else -> simpleName
            }
    }
}

/**
 * Context describing the expected type at a completion location.
 */
@Serializable
public data class ExpectedTypeContext(
    /**
     * The expected type at this location, if known.
     */
    val expectedType: TypeInfo? = null,
    /**
     * The kind of context (assignment, argument, return, etc.).
     */
    val contextKind: TypeContextKind = TypeContextKind.Unknown,
    /**
     * For function arguments, the parameter name if known.
     */
    val parameterName: String? = null,
    /**
     * For function arguments, the parameter index (0-based).
     */
    val parameterIndex: Int? = null,
    /**
     * Whether this context allows nullable types.
     */
    val allowsNull: Boolean = true,
    /**
     * Multiple acceptable types (for overloaded functions).
     */
    val alternativeTypes: List<TypeInfo> = emptyList(),
) {
    /**
     * Check if a type is compatible with this context.
     */
    public fun isCompatible(type: TypeInfo): Boolean {
        // Check nullability
        if (!allowsNull && type.isNullable) {
            return false
        }

        // Check against expected type
        if (expectedType != null && type.isAssignableTo(expectedType)) {
            return true
        }

        // Check alternatives
        return alternativeTypes.any { type.isAssignableTo(it) }
    }

    /**
     * Calculate a compatibility score (higher is better).
     */
    public fun compatibilityScore(type: TypeInfo): Int {
        if (expectedType == null) return 0

        // Exact match
        if (type.qualifiedName == expectedType.qualifiedName) {
            return 100
        }

        // Assignable
        if (type.isAssignableTo(expectedType)) {
            return 50
        }

        // Check alternatives
        for (alt in alternativeTypes) {
            if (type.qualifiedName == alt.qualifiedName) {
                return 80
            }
            if (type.isAssignableTo(alt)) {
                return 40
            }
        }

        return 0
    }

    public companion object {
        /** No type context available. */
        public val None: ExpectedTypeContext = ExpectedTypeContext()
    }
}

/**
 * The kind of type context at a completion location.
 */
@Serializable
public enum class TypeContextKind {
    /**
     * Unknown or unspecified context.
     */
    Unknown,

    /**
     * Variable assignment (val x: Type = |).
     */
    Assignment,

    /**
     * Function argument (foo(|)).
     */
    Argument,

    /**
     * Return statement (return |).
     */
    Return,

    /**
     * If condition (if (|)).
     */
    Condition,

    /**
     * When subject (when (|)).
     */
    WhenSubject,

    /**
     * When branch result.
     */
    WhenBranch,

    /**
     * Type annotation (val x: |).
     */
    TypeAnnotation,

    /**
     * Generic type parameter (List<|>).
     */
    TypeParameter,

    /**
     * Array/list index access (list[|]).
     */
    Index,

    /**
     * Binary operator right side (a + |).
     */
    BinaryOperator,

    /**
     * Thrown exception (throw |).
     */
    Throw,

    /**
     * Catch clause type (catch (e: |)).
     */
    CatchType,
}

/**
 * Type compatibility result for smart completion ranking.
 */
@Serializable
public enum class TypeCompatibility {
    /**
     * Exact type match.
     */
    Exact,

    /**
     * Type is a subtype of expected.
     */
    Subtype,

    /**
     * Type matches an alternative.
     */
    Alternative,

    /**
     * Type is related but not directly compatible.
     */
    Related,

    /**
     * Types are not compatible.
     */
    Incompatible,
}
