package fr.husi.database

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import fr.husi.compose.theme.RED
import fr.husi.database.preference.DataStorePreferenceDataStore
import fr.husi.database.preference.string

object DataStore : ApplicationPreferences(
    DataStorePreferenceDataStore.create(
        PreferenceDataStoreFactory.create(
            storage = WebLocalStorage(PreferencesSerializer, "husi.settings"),
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ),
    ),
    defaultTheme = { RED },
) {
    private const val KEY_CLASH_STORAGE_BACKUP_KEY = "clashStorageBackupKey"
    const val DEFAULT_CLASH_STORAGE_BACKUP_KEY = "husi"

    val clashStorageBackupKey = configurationStore.string(KEY_CLASH_STORAGE_BACKUP_KEY) {
        DEFAULT_CLASH_STORAGE_BACKUP_KEY
    }
}
