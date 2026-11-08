package fr.husi.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal val json = Json { ignoreUnknownKeys = true }

internal fun parseObject(value: String) = json.parseToJsonElement(value) as JsonObject

internal fun JsonObject.text(key: String): String =
    (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

internal fun JsonObject.number(key: String): Long = (get(key) as? JsonPrimitive)?.longOrNull ?: 0

internal fun JsonObject.flag(key: String): Boolean = text(key) == "true"

internal fun JsonObject.obj(key: String): JsonObject =
    get(key) as? JsonObject ?: JsonObject(emptyMap())

internal fun JsonObject.items(key: String): List<JsonObject> =
    (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

internal fun JsonObject.strings(key: String): List<String> =
    (get(key) as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()

internal fun request(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
    pairs.forEach { (key, value) -> put(key, value) }
}
