package su.kidoz.jetaprog.app.viewmodel

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import su.kidoz.jetaprog.app.ui.agent.AgentQuestion
import su.kidoz.jetaprog.app.ui.agent.AgentQuestionField
import java.net.URI
import java.time.LocalDate
import java.time.OffsetDateTime

/** Parses and validates flat ACP form schemas; unsupported shapes fail explicitly. */
internal class AgentQuestionForm(
    private val schema: JsonObject,
) {
    private val properties = schema["properties"]?.jsonObject ?: error("Question has no fields")
    private val required = (schema["required"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }.toSet()

    init {
        require(schema.text("type") == "object") { "Question schema must be a flat object" }
        require(properties.size <= MAX_QUESTION_FIELDS) { "Question has too many fields" }
        require(required.all { it in properties }) { "Question references missing required fields" }
    }

    fun question(
        id: String,
        message: String,
    ): AgentQuestion {
        val fields =
            properties.map { (key, raw) ->
                val property = raw.jsonObject
                val type = property.text("type")
                require(
                    type in setOf("string", "number", "integer", "boolean", "array"),
                ) { "Unsupported question field type" }
                // Do not evaluate arbitrary agent-supplied regular expressions on the UI thread.
                require(property["pattern"] == null || property["pattern"] == JsonNull) {
                    "Pattern-constrained questions are not supported"
                }
                val choices = if (type == "boolean") mapOf("true" to "Yes", "false" to "No") else choices(property)
                require(type != "array" || choices.isNotEmpty()) { "Question arrays require explicit choices" }
                AgentQuestionField(
                    key,
                    property.text("title") ?: key,
                    property.text("description"),
                    type!!,
                    key in required,
                    choices,
                )
            }
        val defaults =
            properties
                .mapNotNull { (key, raw) ->
                    when (val value = raw.jsonObject["default"]) {
                        null, JsonNull -> null
                        is JsonArray -> key to value.map { it.jsonPrimitive.content }
                        is JsonPrimitive -> key to listOf(value.content)
                        else -> error("Unsupported question default")
                    }
                }.toMap()
        return AgentQuestion(id, message, fields, defaults)
    }

    fun content(question: AgentQuestion): JsonObject =
        JsonObject(
            buildMap {
                for (field in question.fields) {
                    val answers = question.answers[field.id].orEmpty()
                    require(!field.required || answers.isNotEmpty()) { "${field.title} is required" }
                    if (answers.isEmpty()) continue
                    val property = properties.getValue(field.id).jsonObject
                    if (field.choices.isNotEmpty()) {
                        require(
                            answers.all {
                                it in field.choices
                            },
                        ) { "Choose a listed value for ${field.title}" }
                    }
                    val value =
                        if (field.type == "array") {
                            require(answers.distinct().size == answers.size) { "Duplicate selections" }
                            checkSize(answers.size, property, "minItems", "maxItems", field.title)
                            JsonArray(answers.map(::JsonPrimitive))
                        } else {
                            require(answers.size == 1) { "Choose one value for ${field.title}" }
                            primitive(field, property, answers.single())
                        }
                    put(field.id, value)
                }
            },
        )

    private fun primitive(
        field: AgentQuestionField,
        property: JsonObject,
        text: String,
    ): JsonElement =
        when (field.type) {
            "boolean" -> {
                JsonPrimitive(text.toBooleanStrictOrNull() ?: error("${field.title} must be Yes or No"))
            }

            "integer", "number" -> {
                val value =
                    if (field.type ==
                        "integer"
                    ) {
                        JsonPrimitive(text.toLongOrNull() ?: error("${field.title} must be a whole number"))
                    } else {
                        JsonPrimitive(
                            text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: error("${field.title} must be a number"),
                        )
                    }
                val number = value.doubleOrNull!!
                require(
                    property["minimum"]?.jsonPrimitive?.doubleOrNull?.let {
                        number >= it
                    } != false,
                ) { "${field.title} is below its minimum" }
                require(
                    property["maximum"]?.jsonPrimitive?.doubleOrNull?.let {
                        number <= it
                    } != false,
                ) { "${field.title} is above its maximum" }
                value
            }

            else -> {
                checkSize(text.codePointCount(0, text.length), property, "minLength", "maxLength", field.title)
                require(text.length <= MAX_ANSWER_CHARS) { "${field.title} is too long" }
                validateFormat(property.text("format"), text)
                JsonPrimitive(text)
            }
        }

    private fun choices(property: JsonObject): Map<String, String> {
        val choiceSchema =
            if (property.text("type") ==
                "array"
            ) {
                property["items"]?.jsonObject ?: error("Missing choices")
            } else {
                property
            }
        val enum = choiceSchema["enum"] as? JsonArray
        if (enum != null) return enum.associate { it.jsonPrimitive.content to it.jsonPrimitive.content }
        val titled = (choiceSchema["oneOf"] ?: choiceSchema["anyOf"]) as? JsonArray
        return titled.orEmpty().associate {
            val choice = it.jsonObject
            val value = choice.getValue("const").jsonPrimitive.content
            value to (choice.text("title") ?: value)
        }
    }

    private fun checkSize(
        size: Int,
        property: JsonObject,
        min: String,
        max: String,
        label: String,
    ) {
        require(property[min]?.jsonPrimitive?.intOrNull?.let { size >= it } != false) { "$label is too short" }
        require(property[max]?.jsonPrimitive?.intOrNull?.let { size <= it } != false) { "$label is too long" }
    }

    private fun validateFormat(
        format: String?,
        text: String,
    ) {
        when (format) {
            null -> {
                return
            }

            "date" -> {
                LocalDate.parse(text)
            }

            "date-time" -> {
                OffsetDateTime.parse(text)
            }

            "uri" -> {
                require(URI(text).isAbsolute) { "Enter an absolute URI" }
            }

            "email" -> {
                require(
                    text.count { it == '@' } == 1 && text.none { it.isWhitespace() } && !text.startsWith('@') &&
                        !text.endsWith('@'),
                ) { "Enter an email address" }
            }

            else -> {
                error("Unsupported question format")
            }
        }
    }
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

private const val MAX_QUESTION_FIELDS = 32
private const val MAX_ANSWER_CHARS = 16_384
