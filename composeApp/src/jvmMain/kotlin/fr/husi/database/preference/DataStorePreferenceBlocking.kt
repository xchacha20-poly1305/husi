package fr.husi.database.preference

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.runBlocking

// String accessors are retained for legacy native callers.

fun DataStorePreferenceDataStore.getString(key: String): String? = runBlocking {
    readValue(stringPreferencesKey(key))
}

fun DataStorePreferenceDataStore.getStringSet(key: String): Set<String>? = runBlocking {
    readValue(stringSetPreferencesKey(key))
}

fun DataStorePreferenceDataStore.reset() = runBlocking {
    edit { prefs ->
        prefs.clear()
    }
}

fun DataStorePreferenceDataStore.putString(key: String, value: String?) {
    if (value == null) {
        remove(key)
        return
    }
    runBlocking {
        writeValue(stringPreferencesKey(key), value)
    }
}

fun DataStorePreferenceDataStore.putStringSet(key: String, values: Set<String>?) {
    if (values == null) {
        remove(key)
        return
    }
    runBlocking {
        writeValue(stringSetPreferencesKey(key), values)
    }
}

fun DataStorePreferenceDataStore.remove(key: String) {
    runBlocking {
        edit { prefs ->
            prefs.remove(booleanPreferencesKey(key))
            prefs.remove(floatPreferencesKey(key))
            prefs.remove(intPreferencesKey(key))
            prefs.remove(longPreferencesKey(key))
            prefs.remove(stringPreferencesKey(key))
            prefs.remove(stringSetPreferencesKey(key))
        }
    }
}

