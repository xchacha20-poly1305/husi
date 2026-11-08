package fr.husi.database.preference

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class PreferenceProxy<T> internal constructor(
    val key: String,
    /**
     * Also reached by blocking JVM callers on the UI thread, so this is
     * deliberately not `suspend`: a default that waits on the database or the
     * filesystem deadlocks the first frame. A preference whose fallback needs I/O
     * belongs in a suspend resolver next to the preference instead.
     */
    internal val defaultValue: () -> T,
    private val read: suspend () -> T?,
    private val write: suspend (T) -> Unit,
    private val observe: () -> Flow<T?>,
    private val updateAtomic: suspend ((T) -> T) -> Unit,
) {
    suspend fun get(): T = read() ?: defaultValue()

    suspend fun getOrNull(): T? = read()

    suspend fun set(value: T) {
        write(value)
    }

    suspend fun update(transform: (T) -> T) {
        updateAtomic(transform)
    }

    fun flow(): Flow<T> = observe().distinctUntilChanged().map { it ?: defaultValue() }
}

private fun <T> DataStorePreferenceDataStore.typedProxy(
    name: String,
    preferenceKey: Preferences.Key<T>,
    defaultValue: () -> T,
): PreferenceProxy<T> = PreferenceProxy(
    key = name,
    defaultValue = defaultValue,
    read = { readValue(preferenceKey) },
    write = { writeValue(preferenceKey, it) },
    observe = { valueFlow(preferenceKey) },
    updateAtomic = { transform ->
        updateValue(preferenceKey) { current -> transform(current ?: defaultValue()) }
    },
)

fun DataStorePreferenceDataStore.string(
    name: String,
    defaultValue: () -> String = { "" },
): PreferenceProxy<String> = typedProxy(name, stringPreferencesKey(name), defaultValue)

fun DataStorePreferenceDataStore.boolean(
    name: String,
    defaultValue: () -> Boolean = { false },
): PreferenceProxy<Boolean> = typedProxy(name, booleanPreferencesKey(name), defaultValue)

fun DataStorePreferenceDataStore.int(
    name: String,
    defaultValue: () -> Int = { 0 },
): PreferenceProxy<Int> {
    val preferenceKey = longPreferencesKey(name)
    return PreferenceProxy(
        key = name,
        defaultValue = defaultValue,
        read = { readValue(preferenceKey)?.toInt() },
        write = { writeValue(preferenceKey, it.toLong()) },
        observe = { valueFlow(preferenceKey).map { it?.toInt() } },
        updateAtomic = { transform ->
            updateValue(preferenceKey) { current ->
                transform(current?.toInt() ?: defaultValue()).toLong()
            }
        },
    )
}

fun DataStorePreferenceDataStore.long(
    name: String,
    defaultValue: () -> Long = { 0L },
): PreferenceProxy<Long> = typedProxy(name, longPreferencesKey(name), defaultValue)

fun DataStorePreferenceDataStore.stringSet(
    name: String,
    defaultValue: () -> Set<String> = { emptySet() },
): PreferenceProxy<Set<String>> = typedProxy(name, stringSetPreferencesKey(name), defaultValue)
