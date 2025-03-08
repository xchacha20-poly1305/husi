@file:Suppress("UNCHECKED_CAST")

package io.nekohasekai.sagernet.ktx

import com.google.gson.annotations.SerializedName
import moe.matsuri.nb4a.utils.JavaUtil.gson
import org.json.JSONObject
import java.lang.reflect.Modifier
import kotlin.reflect.KProperty

operator fun <K, V> Map<K, V>.getValue(thisRef: K, property: KProperty<*>) = get(thisRef)
operator fun <K, V> MutableMap<K, V>.setValue(thisRef: K, property: KProperty<*>, value: V?) {
    if (value != null) {
        put(thisRef, value)
    } else {
        remove(thisRef)
    }
}

typealias JSONMap = MutableMap<String, Any?>

fun String.toJsonMap(): JSONMap = gson.fromJson(this, JSONMap::class.java)

fun toJSONMap(from: Map<*, *>): JSONMap {
    val jsonMap = mutableMapOf<String, Any?>()
    for (field in from) {
        // JSON's key must be a string
        val key = field.key as? String ?: continue
        jsonMap[key] = when (val value = field.value) {
            is Map<*, *> -> toJSONMap(value)
            else -> if (shouldAsMap(value)) {
                value?.asMap()
            } else {
                value
            }
        }
    }
    return jsonMap
}

fun <T : Any> T.asMap(): JSONMap {
    if (!shouldAsMap(this)) throw RuntimeException("invalid type to as map: " + javaClass.name)

    val map = mutableMapOf<String, Any?>()

    var clazz: Class<*> = this.javaClass
    // Traverse the class hierarchy
    while (clazz != Any::class.java) {
        for (field in clazz.declaredFields) {
            field.isAccessible = true

            if (Modifier.isStatic(field.modifiers)) continue
            // Get the field value and process it
            val value = mappedValue(field.get(this)) ?: continue

            // Get SerializedName annotation or fallback to field name
            val key = field.getAnnotation(SerializedName::class.java)?.value ?: field.name

            map[key] = if (value is Map<*, *>) {
                toJSONMap(value)
            } else {
                value
            }
        }
        clazz = clazz.superclass as Class<*>
    }

    return map
}

private fun shouldAsMap(value: Any?): Boolean = when (value) {
    null, is String, is Number, is Boolean, is Map<*, *>, is List<*> -> false
    else -> true
}

private fun mappedValue(value: Any?): Any? = when (value) {
    null -> null

    is List<*> -> if (value.isEmpty()) {
        null
    } else {
        value.mapX {
            if (shouldAsMap(it)) {
                it?.asMap()
            } else {
                it
            }
        }
    }

    is String, is Number, is Boolean, is Map<*, *> -> value

    else -> value.asMap()
}

fun mergeJson(from: JSONMap, to: JSONMap, listAppend: Boolean = false) {
    for (fromField in from) {
        val key = fromField.key // always use in from and to

        when (val fromValue = fromField.value) {
            null -> {}

            is Map<*, *> -> {
                val toValue = to[key]
                val jsonValue = toJSONMap(fromValue)
                when (toValue) {
                    null -> to[key] = jsonValue
                    is Map<*, *> -> mergeJson(jsonValue, toJSONMap(toValue))
                }
            }

            is List<*> -> {
                val toValue = to[key]
                to[key] = if (!listAppend) {
                    fromValue
                } else {
                    if (toValue is List<*>) {
                        toValue + fromValue
                    } else {
                        listOf(toValue) + fromValue
                    }
                }
            }

            else -> to[key] = if (shouldAsMap(fromValue)) {
                val mergedMap = (to[key] as? Map<*, *>)?.let { toJSONMap(it) } ?: mutableMapOf()
                mergeJson(fromValue.asMap(), mergedMap)
                to[key] = mergedMap
            } else {
                fromValue
            }
        }
    }
}

val JSONObject.map: LinkedHashMap<String, Any?>
    get() = javaClass.getDeclaredField("nameValuePairs").let {
        it.isAccessible = true
        it.get(this)
    } as LinkedHashMap<String, Any?>
