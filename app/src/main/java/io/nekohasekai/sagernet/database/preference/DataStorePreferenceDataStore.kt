package io.nekohasekai.sagernet.database.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.nekohasekai.sagernet.ktx.getArray
import io.nekohasekai.sagernet.ktx.getBool
import io.nekohasekai.sagernet.ktx.getDoubleOrNull
import io.nekohasekai.sagernet.ktx.getIntOrNull
import io.nekohasekai.sagernet.ktx.getLongOrNull
import io.nekohasekai.sagernet.ktx.getObject
import io.nekohasekai.sagernet.ktx.getStr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import okio.buffer
import okio.sink
import okio.source
import org.json.JSONArray

private val Context.appDataStore: DataStore<Preferences>
    get() = MultiProcessPreferenceDataStoreHolder.get(this)

private object MultiProcessPreferenceDataStoreHolder {
    @Volatile
    private var instance: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> {
        val appContext = context.applicationContext
        return instance ?: synchronized(this) {
            instance ?: MultiProcessDataStoreFactory.create(
                serializer = MultiProcessPreferencesSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                migrations = listOf(RoomToDataStoreMigration(appContext)),
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { appContext.preferencesDataStoreFile("configuration") }
            ).also { instance = it }
        }
    }
}

private object MultiProcessPreferencesSerializer : Serializer<Preferences> {
    override val defaultValue: Preferences
        get() = PreferencesSerializer.defaultValue

    override suspend fun readFrom(input: InputStream): Preferences {
        return input.source().buffer().use { buffered ->
            PreferencesSerializer.readFrom(buffered)
        }
    }

    override suspend fun writeTo(t: Preferences, output: OutputStream) {
        output.sink().buffer().use { buffered ->
            PreferencesSerializer.writeTo(t, buffered)
            buffered.flush()
        }
    }
}

@Suppress("MemberVisibilityCanBePrivate", "unused", "UNCHECKED_CAST")
class DataStorePreferenceDataStore private constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferenceDataStore {

    companion object {

        private const val KEY_BACKUP_VERSION = "__version"
        private const val BACKUP_VERSION = 2L

        private const val FIELD_TYPE = "type"
        private const val FIELD_VALUE = "value"

        fun create(context: Context): DataStorePreferenceDataStore {
            return DataStorePreferenceDataStore(context.applicationContext.appDataStore)
        }
    }

    private val flowScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val preferencesFlow: StateFlow<Preferences> =
        dataStore.data.stateIn(flowScope, SharingStarted.WhileSubscribed(5_000), emptyPreferences())

    fun getBoolean(key: String): Boolean? = runBlocking {
        dataStore.data.first()[booleanPreferencesKey(key)]
    }

    fun getFloat(key: String): Float? = runBlocking {
        dataStore.data.first()[floatPreferencesKey(key)]
    }

    fun getInt(key: String): Int? = runBlocking {
        dataStore.data.first()[longPreferencesKey(key)]?.toInt()
    }

    fun getLong(key: String): Long? = runBlocking {
        dataStore.data.first()[longPreferencesKey(key)]
    }

    fun getString(key: String): String? = runBlocking {
        dataStore.data.first()[stringPreferencesKey(key)]
    }

    fun getStringSet(key: String): Set<String>? = runBlocking {
        dataStore.data.first()[stringSetPreferencesKey(key)]
    }

    fun booleanFlow(key: String, default: Boolean = false): Flow<Boolean> =
        preferencesFlow.map { it[booleanPreferencesKey(key)] ?: default }.distinctUntilChanged()

    fun floatFlow(key: String, default: Float = 0f): Flow<Float> =
        preferencesFlow.map { it[floatPreferencesKey(key)] ?: default }.distinctUntilChanged()

    fun intFlow(key: String, default: Int = 0): Flow<Int> =
        preferencesFlow.map { (it[longPreferencesKey(key)] ?: default.toLong()).toInt() }
            .distinctUntilChanged()

    fun longFlow(key: String, default: Long = 0L): Flow<Long> =
        preferencesFlow.map { it[longPreferencesKey(key)] ?: default }.distinctUntilChanged()

    fun stringFlow(key: String, default: String = ""): Flow<String> =
        preferencesFlow.map { it[stringPreferencesKey(key)] ?: default }.distinctUntilChanged()

    fun stringSetFlow(key: String, default: Set<String> = emptySet()): Flow<Set<String>> =
        preferencesFlow.map { it[stringSetPreferencesKey(key)] ?: default }.distinctUntilChanged()

    fun reset() = runBlocking {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        getBoolean(key) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        getFloat(key) ?: defValue

    override fun getInt(key: String, defValue: Int): Int =
        getInt(key) ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        getLong(key) ?: defValue

    override fun getString(key: String, defValue: String?): String? =
        getString(key) ?: defValue

    override fun getStringSet(key: String, defValue: MutableSet<String>?): MutableSet<String>? =
        getStringSet(key)?.toMutableSet() ?: defValue

    fun putBoolean(key: String, value: Boolean?) =
        if (value == null) remove(key) else putBoolean(key, value)

    fun putFloat(key: String, value: Float?) =
        if (value == null) remove(key) else putFloat(key, value)

    fun putInt(key: String, value: Int?) =
        if (value == null) remove(key) else putLong(key, value.toLong())

    fun putLong(key: String, value: Long?) =
        if (value == null) remove(key) else putLong(key, value)

    override fun putBoolean(key: String, value: Boolean) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[booleanPreferencesKey(key)] = value
            }
        }
        fireChangeListener(key)
    }

    override fun putFloat(key: String, value: Float) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[floatPreferencesKey(key)] = value
            }
        }
        fireChangeListener(key)
    }

    override fun putInt(key: String, value: Int) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[longPreferencesKey(key)] = value.toLong()
            }
        }
        fireChangeListener(key)
    }

    override fun putLong(key: String, value: Long) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[longPreferencesKey(key)] = value
            }
        }
        fireChangeListener(key)
    }

    override fun putString(key: String, value: String?) {
        if (value == null) {
            remove(key)
            return
        }
        runBlocking {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey(key)] = value
            }
        }
        fireChangeListener(key)
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        if (values == null) {
            remove(key)
            return
        }
        runBlocking {
            dataStore.edit { prefs ->
                prefs[stringSetPreferencesKey(key)] = values
            }
        }
        fireChangeListener(key)
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
        fireChangeListener(key)
    }

    private val listeners = HashSet<OnPreferenceDataStoreChangeListener>()

    private fun fireChangeListener(key: String) {
        val listeners = synchronized(listeners) {
            listeners.toList()
        }
        listeners.forEach { it.onPreferenceDataStoreChanged(this, key) }
    }

    fun registerChangeListener(listener: OnPreferenceDataStoreChangeListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun unregisterChangeListener(listener: OnPreferenceDataStoreChangeListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    suspend fun importLegacyPairs(pairs: List<KeyValuePair>) {
        if (pairs.isEmpty()) {
            dataStore.edit { it.clear() }
            return
        }
        val changedKeys = HashSet<String>()
        dataStore.edit { prefs ->
            prefs.clear()
            pairs.forEach { pair ->
                val key = pair.key
                when (key) {
                    RoomToDataStoreMigration.MIGRATION_KEY -> {
                        return@forEach
                    }

                    KEY_BACKUP_VERSION -> pair.long?.let {
                        if (it != BACKUP_VERSION) {
                            throw IllegalArgumentException("Backup version mismatch: $it")
                        }
                        return@forEach
                    }
                }
                @Suppress("DEPRECATION")
                when (pair.valueType) {
                    KeyValuePair.TYPE_BOOLEAN -> pair.boolean?.let {
                        prefs[booleanPreferencesKey(key)] = it
                        changedKeys += key
                    }

                    KeyValuePair.TYPE_FLOAT -> pair.float?.let {
                        prefs[floatPreferencesKey(key)] = it
                        changedKeys += key
                    }

                    KeyValuePair.TYPE_LONG,
                    KeyValuePair.TYPE_INT,
                        -> pair.long?.let {
                        prefs[longPreferencesKey(key)] = it
                        changedKeys += key
                    }

                    KeyValuePair.TYPE_STRING -> pair.string?.let {
                        prefs[stringPreferencesKey(key)] = it
                        changedKeys += key
                    }

                    KeyValuePair.TYPE_STRING_SET -> pair.stringSet?.let {
                        prefs[stringSetPreferencesKey(key)] = it
                        changedKeys += key
                    }
                }
            }
        }
        changedKeys.forEach { fireChangeListener(it) }
    }

    suspend fun exportToJson(json: JSONObject) {
        val preferences = dataStore.data.first()
        json.put(KEY_BACKUP_VERSION, BACKUP_VERSION)
        preferences.asMap().forEach { (key, value) ->
            if (key.name == RoomToDataStoreMigration.MIGRATION_KEY) return@forEach
            json.put(key.name, toValueHolder(value))
        }
    }

    suspend fun exportToString(): String {
        val preferences = dataStore.data.first()
        return buildString {
            preferences.asMap().forEach { (key, value) ->
                if (key.name == RoomToDataStoreMigration.MIGRATION_KEY) return@forEach
                append(key.name)
                append(": ")
                append(value)
                append("\n")
            }
        }
    }

    suspend fun importFromJson(json: JSONObject) {
        val version = json.getLongOrNull(KEY_BACKUP_VERSION)
        if (version != BACKUP_VERSION) {
            throw IllegalArgumentException("Backup version mismatch: $version")
        }

        val changedKeys = HashSet<String>()
        dataStore.edit { prefs ->
            prefs.clear()

            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == KEY_BACKUP_VERSION || key == RoomToDataStoreMigration.MIGRATION_KEY) continue

                val holder = json.getObject(key) ?: error("invalid holder of key: $key")
                val type = ValueType.fromJson(holder.getIntOrNull(FIELD_TYPE) ?: error("missing type for key: $key"))
                when (type) {
                    ValueType.BOOLEAN -> {
                        val value = holder.getBool(FIELD_VALUE) ?: false
                        prefs[booleanPreferencesKey(key)] = value
                        changedKeys += key
                    }

                    ValueType.FLOAT -> {
                        val value = holder.getDoubleOrNull(FIELD_VALUE)?.toFloat() ?: 0f
                        prefs[floatPreferencesKey(key)] = value
                        changedKeys += key
                    }

                    ValueType.INT, ValueType.LONG -> {
                        val value = holder.getLongOrNull(FIELD_VALUE)
                        value?.let { prefs[longPreferencesKey(key)] = it }
                        if (value != null) changedKeys += key
                    }

                    ValueType.STRING -> {
                        val value = holder.getStr(FIELD_VALUE)
                        value?.let { prefs[stringPreferencesKey(key)] = it }
                        if (value != null) changedKeys += key
                    }

                    ValueType.STRING_SET -> {
                        val value = holder.getArray(FIELD_VALUE)
                        val set = buildSet {
                            if (value != null) {
                                for (i in 0 until value.length()) add(value.getString(i))
                            }
                        }
                        prefs[stringSetPreferencesKey(key)] = set
                        changedKeys += key
                    }
                }
            }
        }
        changedKeys.forEach { fireChangeListener(it) }
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

    private fun toValueHolder(value: Any): JSONObject {
        val jsonObject = JSONObject()
        val type = ValueType.inferOf(value)
        jsonObject.put(FIELD_TYPE, type.code)
        when (type) {
            ValueType.BOOLEAN -> jsonObject.put(FIELD_VALUE, value as Boolean)
            ValueType.FLOAT -> jsonObject.put(FIELD_VALUE, (value as Float).toDouble())
            ValueType.INT -> jsonObject.put(FIELD_VALUE, value as Int)
            ValueType.LONG -> jsonObject.put(FIELD_VALUE, value as Long)
            ValueType.STRING -> jsonObject.put(FIELD_VALUE, value as String)
            ValueType.STRING_SET -> {
                val arr = JSONArray((value as Set<String>).toList())
                jsonObject.put(FIELD_VALUE, arr)
            }
        }
        return jsonObject
    }

    private fun applyValueHolder(key: String, holder: JSONObject) {
        val type = ValueType.fromJson(holder.getIntOrNull(FIELD_TYPE) ?: error("missing type"))
        when (type) {
            ValueType.BOOLEAN -> putBoolean(key, holder.getBool(FIELD_VALUE) ?: false)
            ValueType.FLOAT -> putFloat(key, holder.getDoubleOrNull(FIELD_VALUE)?.toFloat() ?: 0f)
            ValueType.INT -> holder.getIntOrNull(FIELD_VALUE)?.let { putInt(key, it) }
            ValueType.LONG -> holder.getLongOrNull(FIELD_VALUE)?.let { putLong(key, it) }
            ValueType.STRING -> holder.getStr(FIELD_VALUE)?.let { putString(key, it) }
            ValueType.STRING_SET -> {
                val arr = holder.getArray(FIELD_VALUE)
                val set = buildSet {
                    if (arr != null) {
                        for (i in 0 until arr.length()) add(arr.getString(i))
                    }
                }
                putStringSet(key, set.toMutableSet())
            }
        }
    }
}
