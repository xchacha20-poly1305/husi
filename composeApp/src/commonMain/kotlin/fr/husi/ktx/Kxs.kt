package fr.husi.ktx

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

val kxs: Json = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun String.toJsonMapKxs(): JSONMap {
    val element = kxs.parseToJsonElement(this)
    return element.toJsonMapKxs()
}

fun JsonElement.toJsonMapKxs(): JSONMap {
    val obj = this as? JsonObject ?: error("JSON root is not an object")
    val map = LinkedHashMap<String, Any?>(obj.size)
    for ((key, value) in obj) {
        map[key] = value.toAnyKxs()
    }
    return map
}

fun JSONMap.toJsonElementKxs(): JsonElement = anyToJsonElementKxs(this)

fun JSONMap.toJsonObjectKxs(): JsonObject = toJsonElementKxs() as JsonObject

inline fun <reified T> T.asKxsMap(): JSONMap = kxs.encodeToJsonElement(serializer<T>(), this).toJsonMapKxs()

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
fun Any.toJsonObjectKxs(): JsonObject {
    @Suppress("UNCHECKED_CAST")
    val serializer = this::class.serializer() as KSerializer<Any>
    val element = kxs.encodeToJsonElement(serializer, this)
    return element as? JsonObject ?: error("JSON root is not an object")
}

private fun JsonElement.toAnyKxs(): Any? = when (this) {
    JsonNull -> null
    is JsonPrimitive -> toPrimitiveKxs()
    is JsonArray -> map { it.toAnyKxs() }
    is JsonObject -> toJsonMapKxs()
}

private fun JsonPrimitive.toPrimitiveKxs(): Any {
    if (isString) return content
    booleanOrNull?.let { return it }
    longOrNull?.let { return it }
    doubleOrNull?.let { return it }
    return content
}

fun JSONMap.getStr(name: String): String? = (this[name] as? String)?.blankAsNull()
fun JSONMap.getBool(name: String): Boolean? = this[name] as? Boolean
fun JSONMap.getIntOrNull(name: String): Int? = (this[name] as? Number)?.toInt()
fun JSONMap.getLongOrNull(name: String): Long? = (this[name] as? Number)?.toLong()
fun JSONMap.getDoubleOrNull(name: String): Double? = (this[name] as? Number)?.toDouble()
fun JSONMap.getObject(name: String): JSONMap? {
    val v = this[name] ?: return null
    if (v is Map<*, *>) {
        @Suppress("UNCHECKED_CAST")
        return v as JSONMap
    }
    return null
}
fun JSONMap.getArray(name: String): List<*>? = this[name] as? List<*>

fun JSONMap.toJsonStringKxs(): String =
    kxs.encodeToString(JsonElement.serializer(), filterNulls().toJsonElementKxs())

private fun JSONMap.filterNulls(): JSONMap {
    val result: JSONMap = mutableMapOf()
    for ((key, value) in this) {
        if (value == null) continue
        @Suppress("UNCHECKED_CAST")
        result[key] = when (value) {
            is Map<*, *> -> (value as JSONMap).filterNulls()
            is List<*> -> value.map { if (it is Map<*, *>) (it as JSONMap).filterNulls() else it }
            else -> value
        }
    }
    return result
}

private fun anyToJsonElementKxs(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> {
        val builder = LinkedHashMap<String, JsonElement>(value.size)
        for ((key, mapValue) in value) {
            val stringKey = key?.toString() ?: continue
            builder[stringKey] = anyToJsonElementKxs(mapValue)
        }
        JsonObject(builder)
    }

    is List<*> -> JsonArray(value.map { anyToJsonElementKxs(it) })
    is Set<*> -> JsonArray(value.map { anyToJsonElementKxs(it) })
    else -> JsonPrimitive(value.toString())
}
