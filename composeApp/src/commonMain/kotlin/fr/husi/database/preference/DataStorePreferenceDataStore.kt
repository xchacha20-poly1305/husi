package fr.husi.database.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Suppress("UNCHECKED_CAST")
class DataStorePreferenceDataStore private constructor(
    private val dataStore: DataStore<Preferences>,
) {

    companion object {

        internal const val KEY_BACKUP_VERSION = "__version"
        internal const val BACKUP_VERSION = 2L

        private const val KEY_MIGRATION = "__datastore_migrated_from_room__"

        private const val FIELD_TYPE = "type"
        private const val FIELD_VALUE = "value"

        fun create(dataStore: DataStore<Preferences>): DataStorePreferenceDataStore {
            return DataStorePreferenceDataStore(dataStore)
        }
    }

    suspend fun <T> readValue(key: Preferences.Key<T>): T? = dataStore.data.first()[key]

    suspend fun <T> writeValue(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    fun <T> valueFlow(key: Preferences.Key<T>): Flow<T?> = dataStore.data.map { it[key] }

    suspend fun <T> updateValue(
        key: Preferences.Key<T>,
        transform: suspend (T?) -> T,
    ) {
        dataStore.edit { it[key] = transform(it[key]) }
    }

    internal suspend fun edit(
        transform: suspend (MutablePreferences) -> Unit,
    ) {
        dataStore.edit(transform)
    }

     // (put|get)String only for legacy usage. Do not use.

    fun getString(key: String): String? = runBlocking {
        readValue(stringPreferencesKey(key))
    }

    fun getStringSet(key: String): Set<String>? = runBlocking {
        readValue(stringSetPreferencesKey(key))
    }

    fun keysFlow(vararg keys: String, emitInitialState: Boolean = false): Flow<Unit> {
        val keysToWatch = keys.toSet()

        val flow = dataStore.data
            .map { prefs ->
                prefs.asMap()
                    .filter { (key, _) -> key.name in keysToWatch }
                    .values
                    .toList()
            }
            .distinctUntilChanged()
            .map { }

        return if (emitInitialState) {
            flow
        } else {
            flow.drop(1)
        }
    }

    fun reset() = runBlocking {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    fun putString(key: String, value: String?) {
        if (value == null) {
            remove(key)
            return
        }
        runBlocking {
            writeValue(stringPreferencesKey(key), value)
        }
    }

    fun putStringSet(key: String, values: Set<String>?) {
        if (values == null) {
            remove(key)
            return
        }
        runBlocking {
            writeValue(stringSetPreferencesKey(key), values)
        }
    }

    fun remove(key: String) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs.remove(booleanPreferencesKey(key))
                prefs.remove(floatPreferencesKey(key))
                prefs.remove(intPreferencesKey(key))
                prefs.remove(longPreferencesKey(key))
                prefs.remove(stringPreferencesKey(key))
                prefs.remove(stringSetPreferencesKey(key))
            }
        }
    }

    suspend fun exportToJson(): JsonObject {
        val preferences = dataStore.data.first()
        val map = mutableMapOf<String, JsonElement>()
        map[KEY_BACKUP_VERSION] = JsonPrimitive(BACKUP_VERSION)
        preferences.asMap().forEach { (key, value) ->
            if (key.name == KEY_MIGRATION) return@forEach
            map[key.name] = toValueHolder(value)
        }
        return JsonObject(map)
    }

    suspend fun exportToString(): String {
        val preferences = dataStore.data.first()
        return buildString {
            preferences.asMap().forEach { (key, value) ->
                if (key.name == KEY_MIGRATION) return@forEach
                append(key.name)
                append(": ")
                append(value)
                append("\n")
            }
        }
    }

    suspend fun importFromJson(json: JsonObject) {
        val versionElement = json[KEY_BACKUP_VERSION]
        val version = versionElement?.jsonPrimitive?.longOrNull
        if (version != BACKUP_VERSION) {
            throw IllegalArgumentException("Backup version mismatch: $version")
        }

        val changedKeys = HashSet<String>()
        dataStore.edit { prefs ->
            prefs.clear()

            for ((key, value) in json) {
                if (key == KEY_BACKUP_VERSION || key == KEY_MIGRATION) continue

                val holder = value as? JsonObject ?: error("invalid holder of key: $key")
                val typeElement = holder[FIELD_TYPE]?.jsonPrimitive?.intOrNull
                    ?: error("missing type for key: $key")
                val type = ValueType.fromJson(typeElement)
                when (type) {
                    ValueType.BOOLEAN -> {
                        val v = holder[FIELD_VALUE]?.jsonPrimitive?.booleanOrNull ?: false
                        prefs[booleanPreferencesKey(key)] = v
                        changedKeys += key
                    }

                    ValueType.FLOAT -> {
                        val v = holder[FIELD_VALUE]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f
                        prefs[floatPreferencesKey(key)] = v
                        changedKeys += key
                    }

                    ValueType.INT, ValueType.LONG -> {
                        val v = holder[FIELD_VALUE]?.jsonPrimitive?.longOrNull
                        v?.let { prefs[longPreferencesKey(key)] = it }
                        if (v != null) changedKeys += key
                    }

                    ValueType.STRING -> {
                        val v = holder[FIELD_VALUE]?.jsonPrimitive?.contentOrNull
                        v?.let { prefs[stringPreferencesKey(key)] = it }
                        if (v != null) changedKeys += key
                    }

                    ValueType.STRING_SET -> {
                        val arr = holder[FIELD_VALUE] as? JsonArray
                        val set = buildSet {
                            if (arr != null) {
                                for (element in arr) add(element.jsonPrimitive.content)
                            }
                        }
                        prefs[stringSetPreferencesKey(key)] = set
                        changedKeys += key
                    }
                }
            }
        }
    }

    private enum class ValueType(val code: Int) {
        BOOLEAN(0),
        FLOAT(1),
        INT(2),
        LONG(3),
        STRING(4),
        STRING_SET(5);

        companion object {
            fun fromCode(code: Int): ValueType =
                entries.firstOrNull { it.code == code }
                    ?: throw IllegalArgumentException("Unknown ValueHolder type code: $code")

            fun fromJson(any: Any): ValueType = when (any) {
                is Int -> fromCode(any)
                is Number -> fromCode(any.toInt())
                is String -> entries.firstOrNull { it.name == any }
                    ?: throw IllegalArgumentException("Unknown ValueHolder type name: $any")

                else -> throw IllegalArgumentException("Unsupported 'type' field: $any")
            }

            fun inferOf(value: Any): ValueType = when (value) {
                is Boolean -> BOOLEAN
                is Float -> FLOAT
                is Int -> INT
                is Long -> LONG
                is String -> STRING
                is Set<*> -> STRING_SET
                else -> error("Unsupported value type: ${value::class.java}")
            }
        }
    }

    private fun toValueHolder(value: Any): JsonObject {
        val type = ValueType.inferOf(value)
        val map = mutableMapOf<String, JsonElement>()
        map[FIELD_TYPE] = JsonPrimitive(type.code)
        when (type) {
            ValueType.BOOLEAN -> map[FIELD_VALUE] = JsonPrimitive(value as Boolean)
            ValueType.FLOAT -> map[FIELD_VALUE] = JsonPrimitive((value as Float).toDouble())
            ValueType.INT -> map[FIELD_VALUE] = JsonPrimitive(value as Int)
            ValueType.LONG -> map[FIELD_VALUE] = JsonPrimitive(value as Long)
            ValueType.STRING -> map[FIELD_VALUE] = JsonPrimitive(value as String)
            ValueType.STRING_SET -> {
                map[FIELD_VALUE] = JsonArray((value as Set<*>).map { JsonPrimitive(it as String) })
            }
        }
        return JsonObject(map)
    }

}
