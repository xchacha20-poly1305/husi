package fr.husi.database.preference

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

suspend fun DataStorePreferenceDataStore.importLegacyPairs(pairs: List<KeyValuePair>) {
    if (pairs.isEmpty()) {
        edit { it.clear() }
        return
    }
    val changedKeys = HashSet<String>()
    edit { prefs ->
        prefs.clear()
        pairs.forEach { pair ->
            val key = pair.key
            when (key) {
                RoomToDataStoreMigration.MIGRATION_KEY -> {
                    return@forEach
                }

                DataStorePreferenceDataStore.KEY_BACKUP_VERSION -> pair.long?.let {
                    if (it != DataStorePreferenceDataStore.BACKUP_VERSION) {
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
}
