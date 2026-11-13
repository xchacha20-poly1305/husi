@file:OptIn(InternalSerializationApi::class)

package fr.husi.ktx

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonObject

fun Any.asKxsMap(): JSONMap {
    @Suppress("UNCHECKED_CAST")
    val serializer = this::class.serializer() as KSerializer<Any>
    return kxs.encodeToJsonElement(serializer, this).toJsonMapKxs()
}

fun Any.toJsonObjectKxs(): JsonObject {
    @Suppress("UNCHECKED_CAST")
    val serializer = this::class.serializer() as KSerializer<Any>
    val element = kxs.encodeToJsonElement(serializer, this)
    return element as? JsonObject ?: error("JSON root is not an object")
}

@OptIn(InternalSerializationApi::class)
fun Any.toJsonStringKxs(): String {
    @Suppress("UNCHECKED_CAST")
    val serializer = this::class.serializer() as KSerializer<Any>
    return kxs.encodeToString(serializer, this)
}
