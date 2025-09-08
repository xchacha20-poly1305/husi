@file:Suppress("UNCHECKED_CAST")

package io.nekohasekai.sagernet.ktx

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.google.gson.annotations.SerializedName
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
        value.map {
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

fun mergeJson(from: JSONMap, to: JSONMap) {
    for ((key, fromValue) in from) {
        val prepend = key.startsWith('+')
        val append = key.endsWith('+')
        val realKey = if (append) {
            key.substring(0, key.length - 1)
        } else if (prepend) {
            key.substring(1)
        } else {
            key
        }

        when (fromValue) {
            null -> {}

            is Map<*, *> -> {
                // 递归处理嵌套对象
                val jsonValue = toJSONMap(fromValue)
                when (val toValue = to[realKey]) {
                    null -> to[realKey] = jsonValue
                    is Map<*, *> -> {
                        val toMap = toJSONMap(toValue)
                        mergeJson(jsonValue, toMap)
                        to[realKey] = toMap
                    }

                    else -> to[realKey] = jsonValue
                }
            }

            is List<*> -> {
                val toValue = to[realKey]
                to[realKey] = when {
                    prepend -> prependList(toValue, fromValue)
                    append -> appendList(toValue, fromValue)
                    else -> fromValue
                }
            }

            else -> {
                if (shouldAsMap(fromValue)) {
                    val fromMap = fromValue.asMap()
                    val toValue = to[realKey]
                    if (toValue is Map<*, *>) {
                        val toMap = toJSONMap(toValue)
                        mergeJson(fromMap, toMap)
                        to[realKey] = toMap
                    } else {
                        to[realKey] = fromMap
                    }
                } else {
                    when {
                        prepend -> to[realKey] = prependList(to[realKey], listOf(fromValue))
                        append -> to[realKey] = appendList(to[realKey], listOf(fromValue))
                        else -> to[realKey] = fromValue
                    }
                }
            }
        }
    }
}

private fun prependList(toValue: Any?, fromList: List<*>): List<*> {
    return when (toValue) {
        null -> fromList
        is List<*> -> fromList + toValue
        else -> fromList + listOf(toValue)
    }
}

private fun appendList(toValue: Any?, fromList: List<*>): List<*> {
    return when (toValue) {
        null -> fromList
        is List<*> -> toValue + fromList
        else -> listOf(toValue) + fromList
    }
}

val JSONObject.map: LinkedHashMap<String, Any?>
    get() = javaClass.getDeclaredField("nameValuePairs").let {
        it.isAccessible = true
        it.get(this)
    } as LinkedHashMap<String, Any?>

fun <K, V> Map<K, V>.reverse(): Map<V, K> {
    val map = mutableMapOf<V, K>()
    for ((key, value) in this) {
        map[value] = key
    }
    return map
}

@Suppress("DEPRECATION")
val gson: Gson = GsonBuilder()
    .setPrettyPrinting()
    .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
    .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
    .setLenient()
    .disableHtmlEscaping()
    .create()