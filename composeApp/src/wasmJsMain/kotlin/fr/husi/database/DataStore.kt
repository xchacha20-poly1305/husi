package fr.husi.database

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import fr.husi.compose.theme.RED
import fr.husi.database.preference.DataStorePreferenceDataStore

object DataStore : ApplicationPreferences(
    DataStorePreferenceDataStore.create(
        PreferenceDataStoreFactory.create(
            storage = WebLocalStorage(PreferencesSerializer, "husi.settings"),
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ),
    ),
    defaultTheme = { RED },
)
