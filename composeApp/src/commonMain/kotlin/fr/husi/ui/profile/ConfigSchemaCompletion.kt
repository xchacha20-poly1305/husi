package fr.husi.ui.profile

import fr.husi.libcore.Libcore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

data class ConfigSchemaCompletion(
    val label: String,
    val description: String?,
    val replacement: String,
    val replaceStart: Int,
    val replaceEnd: Int = replaceStart,
    val cursorOffset: Int = replacement.length,
)

private data class SchemaFrame(
    val schema: JsonObject,
    val isArray: Boolean,
    var pendingKey: String? = null,
)

private data class JsonCursor(
    val schema: JsonObject,
    val inKey: Boolean,
    val isInString: Boolean,
    val prefix: String,
    val replaceStart: Int,
    val replaceEnd: Int,
)

class ConfigSchemaCompleter(
    private val root: JsonObject,
) {

    fun complete(text: String, cursor: Int): List<ConfigSchemaCompletion> {
        val context = scan(text, cursor)
        if (!context.isInString && context.prefix.isEmpty()) return emptyList()
        return if (context.inKey) {
            keyCompletions(context)
        } else {
            valueCompletions(context)
        }
    }

    private fun keyCompletions(context: JsonCursor): List<ConfigSchemaCompletion> {
        return propertiesOf(context.schema).mapNotNull { (name, schema) ->
            if (!name.startsWith(context.prefix, ignoreCase = true)) return@mapNotNull null
            val value = scaffold(schema)
            val replacement = "\"$name\": $value"
            ConfigSchemaCompletion(
                label = name,
                description = descriptionOf(schema),
                replacement = replacement,
                replaceStart = context.replaceStart,
                replaceEnd = context.replaceEnd,
                cursorOffset = cursorOffsetForScaffold(replacement, value),
            )
        }
    }

    private fun valueCompletions(context: JsonCursor): List<ConfigSchemaCompletion> {
        val values = mutableListOf<ConfigSchemaCompletion>()
        val choices = variants(context.schema).flatMap { schema ->
            val enum = schema["enum"] as? JsonArray
            enum ?: listOfNotNull(schema["const"])
        }.distinct()
        if (choices.isNotEmpty()) {
            for (value in choices) {
                val primitive = value as? JsonPrimitive ?: continue
                val content = primitive.content
                if (!content.startsWith(context.prefix, ignoreCase = true)) continue
                val replacement = if (primitive.isString) "\"$content\"" else content
                values += ConfigSchemaCompletion(
                    label = content,
                    description = descriptionOf(context.schema),
                    replacement = replacement,
                    replaceStart = context.replaceStart,
                    replaceEnd = context.replaceEnd,
                )
            }
            return values
        }

        when (typeOf(context.schema)) {
            "object" -> values += formCompletion(context, "{}", "object", 1)
            "array" -> values += formCompletion(context, "[]", "array", 1)
            "string" -> if (!context.isInString) {
                values += formCompletion(context, "\"\"", "string", 1)
            }
            "boolean" -> {
                values += ConfigSchemaCompletion("true", null, "true", context.replaceStart)
                values += ConfigSchemaCompletion("false", null, "false", context.replaceStart)
            }
        }
        return values
    }

    private fun formCompletion(
        context: JsonCursor,
        replacement: String,
        label: String,
        cursorOffset: Int,
    ) = ConfigSchemaCompletion(
        label = replacement,
        description = label,
        replacement = replacement,
        replaceStart = context.replaceStart,
        replaceEnd = context.replaceEnd,
        cursorOffset = cursorOffset,
    )

    private fun scaffold(schema: JsonObject): String = when (typeOf(schema)) {
        "object" -> "{}"
        "array" -> "[]"
        "string" -> "\"\""
        "boolean" -> "false"
        else -> ""
    }

    private fun cursorOffsetForScaffold(replacement: String, scaffold: String): Int = when (scaffold) {
        "{}", "[]" -> replacement.length - 1
        "\"\"" -> replacement.length - 1
        else -> replacement.length
    }

    private fun descriptionOf(schema: JsonObject): String? = variants(schema).firstNotNullOfOrNull {
        it["description"]?.jsonPrimitiveContent()
    }

    private fun scan(text: String, cursor: Int): JsonCursor {
        val frames = mutableListOf(SchemaFrame(root, isArray = false))
        var inString = false
        var escaped = false
        var stringStart = -1
        var stringIsKey = false
        var lastString: String? = null
        var lastStringWasKey = false

        for (index in 0 until cursor.coerceAtMost(text.length)) {
            val character = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == '"') {
                    lastString = text.substring(stringStart + 1, index)
                    lastStringWasKey = stringIsKey
                    inString = false
                }
                continue
            }

            when (character) {
                '"' -> {
                    inString = true
                    stringStart = index
                    stringIsKey = !frames.last().isArray && previousMeaningful(text, index - 1) in setOf('{', ',')
                }

                ':' -> if (lastStringWasKey) {
                    frames.last().pendingKey = lastString
                    lastString = null
                }

                '{', '[' -> {
                    if (previousMeaningful(text, index - 1) == null) {
                        continue
                    }
                    val parent = frames.last()
                    val childSchema = if (parent.isArray) {
                        itemsOf(parent.schema)
                    } else {
                        parent.pendingKey?.let { key -> propertiesOf(parent.schema)[key] }
                    } ?: JsonObject(emptyMap())
                    frames += SchemaFrame(childSchema, isArray = character == '[')
                }

                '}', ']' -> if (frames.size > 1) {
                    frames.removeAt(frames.lastIndex)
                }

                ',' -> {
                    frames.last().pendingKey = null
                    lastString = null
                    lastStringWasKey = false
                }
            }
        }

        val frame = frames.last()
        if (inString) {
            val prefix = text.substring(stringStart + 1, cursor.coerceAtMost(text.length))
            val schema = if (stringIsKey) {
                frame.schema
            } else {
                valueSchema(frame)
            }
            val replaceEnd = if (cursor < text.length && text[cursor] == '"') cursor + 1 else cursor
            return JsonCursor(schema, stringIsKey, true, prefix, stringStart, replaceEnd)
        }

        val previous = previousMeaningful(text, cursor - 1)
        val expectsValue = previous == ':' || frame.isArray
        return JsonCursor(
            schema = if (expectsValue) valueSchema(frame) else frame.schema,
            inKey = !expectsValue,
            isInString = false,
            prefix = "",
            replaceStart = cursor,
            replaceEnd = cursor,
        )
    }

    private fun valueSchema(frame: SchemaFrame): JsonObject = if (frame.isArray) {
        itemsOf(frame.schema) ?: JsonObject(emptyMap())
    } else {
        frame.pendingKey?.let { key -> propertiesOf(frame.schema)[key] } ?: JsonObject(emptyMap())
    }

    private fun propertiesOf(schema: JsonObject): Map<String, JsonObject> = variants(schema)
        .flatMap { variant ->
            (variant["properties"] as? JsonObject).orEmpty().mapNotNull { (name, value) ->
                (value as? JsonObject)?.let { name to it }
            }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, schemas) -> combine(schemas) }

    private fun itemsOf(schema: JsonObject): JsonObject? {
        val schemas = variants(schema).mapNotNull { it["items"] as? JsonObject }
        return schemas.takeIf { it.isNotEmpty() }?.let(::combine)
    }

    private fun typeOf(schema: JsonObject): String? = variants(schema).firstNotNullOfOrNull {
        it["type"]?.jsonPrimitiveContent()
    }

    private fun variants(
        schema: JsonObject,
        resolving: Set<String> = emptySet(),
    ): List<JsonObject> {
        val reference = schema[$$"$ref"]?.jsonPrimitiveContent()
        if (reference != null && reference !in resolving) {
            val resolved = resolveReference(reference)
            if (resolved != null) return variants(resolved, resolving + reference)
        }

        val base = JsonObject(schema.filterKeys { it !in schemaCompositionKeys })
        val allOf = schema["allOf"] as? JsonArray
        var resolved = listOf(base)
        if (allOf != null) {
            for (element in allOf) {
                val part = (element as? JsonObject)?.let { variants(it, resolving) }.orEmpty()
                if (part.isNotEmpty()) {
                    resolved = resolved.flatMap { left -> part.map { right -> merge(left, right) } }
                }
            }
        }

        val alternatives = (schema["oneOf"] as? JsonArray) ?: (schema["anyOf"] as? JsonArray)
        if (alternatives == null) return resolved
        return alternatives.flatMap { element ->
            (element as? JsonObject)?.let { variants(it, resolving) }.orEmpty()
        }.flatMap { alternative -> resolved.map { merge(it, alternative) } }
    }

    private fun resolveReference(reference: String): JsonObject? {
        if (!reference.startsWith("#/")) return null
        var current: JsonElement = root
        for (rawPart in reference.removePrefix("#/").split('/')) {
            val part = rawPart.replace("~1", "/").replace("~0", "~")
            current = (current as? JsonObject)?.get(part) ?: return null
        }
        return current as? JsonObject
    }

    private fun combine(schemas: List<JsonObject>): JsonObject = when (schemas.size) {
        1 -> schemas.single()
        else -> JsonObject(mapOf("anyOf" to JsonArray(schemas)))
    }

    private fun merge(left: JsonObject, right: JsonObject): JsonObject {
        val result = left.toMutableMap()
        for ((key, value) in right) {
            val previous = result[key]
            result[key] =
                if (key == "properties" && previous is JsonObject && value is JsonObject) {
                    JsonObject(previous + value)
                } else {
                    value
                }
        }
        return JsonObject(result)
    }

    private fun previousMeaningful(text: String, start: Int): Char? {
        for (i in start downTo 0) {
            if (!text[i].isWhitespace()) return text[i]
        }
        return null
    }

    private fun JsonElement.jsonPrimitiveContent(): String? = (this as? JsonPrimitive)?.content
}

private val schemaCompositionKeys = setOf("allOf", "anyOf", "oneOf")

val configSchemaCompleter by lazy {
    ConfigSchemaCompleter(Json.parseToJsonElement(Libcore.generateConfigSchema()).jsonObject)
}
