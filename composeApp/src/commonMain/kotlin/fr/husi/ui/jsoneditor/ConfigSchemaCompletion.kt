package fr.husi.ui.jsoneditor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    private val jsonEngine: ConfigJsonEngine = ConfigJsonEngine(),
) {

    fun complete(text: String, cursor: Int): List<ConfigSchemaCompletion> {
        val context = scan(jsonEngine.document(text), cursor)
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

    private fun scan(document: ConfigJsonDocument, cursor: Int): JsonCursor {
        val text = document.text
        val frames = mutableListOf(SchemaFrame(root, isArray = false))
        var lastString: String? = null
        var lastStringWasKey = false
        val safeCursor = cursor.coerceIn(0, text.length)

        for (token in document.tokens) {
            if (token.start >= safeCursor) break
            if (token.type == ConfigJsonTokenType.STRING) {
                val stringIsKey = !frames.last().isArray &&
                    previousMeaningful(text, token.start - 1) in setOf('{', ',')
                val isClosed = token.end - token.start >= 2 && text[token.end - 1] == '"'
                val isInside = if (isClosed) safeCursor < token.end else safeCursor <= token.end
                if (isInside) {
                    val contentStart = token.start + 1
                    val contentEnd = safeCursor.coerceAtLeast(contentStart).coerceAtMost(
                        if (isClosed) token.end - 1 else token.end,
                    )
                    val prefix = text.substring(contentStart, contentEnd)
                    val schema = if (stringIsKey) frames.last().schema else valueSchema(frames.last())
                    val replaceEnd = if (safeCursor < text.length && text[safeCursor] == '"') {
                        safeCursor + 1
                    } else {
                        safeCursor
                    }
                    return JsonCursor(schema, stringIsKey, true, prefix, token.start, replaceEnd)
                }
                val contentEnd = token.end - if (isClosed) 1 else 0
                lastString = text.substring(token.start + 1, contentEnd)
                lastStringWasKey = stringIsKey
                continue
            }
            if (token.type != ConfigJsonTokenType.PUNCTUATION) continue
            when (text[token.start]) {
                ':' -> if (lastStringWasKey) {
                    frames.last().pendingKey = lastString
                    lastString = null
                }

                '{', '[' -> {
                    if (previousMeaningful(text, token.start - 1) == null) {
                        continue
                    }
                    val parent = frames.last()
                    val childSchema = if (parent.isArray) {
                        itemsOf(parent.schema)
                    } else {
                        parent.pendingKey?.let { key -> propertiesOf(parent.schema)[key] }
                    } ?: JsonObject(emptyMap())
                    frames += SchemaFrame(childSchema, isArray = text[token.start] == '[')
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
        val previous = previousMeaningful(text, safeCursor - 1)
        val expectsValue = previous == ':' || frame.isArray
        return JsonCursor(
            schema = if (expectsValue) valueSchema(frame) else frame.schema,
            inKey = !expectsValue,
            isInString = false,
            prefix = "",
            replaceStart = safeCursor,
            replaceEnd = safeCursor,
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

