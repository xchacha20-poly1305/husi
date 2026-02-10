package fr.husi.database.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

internal expect fun createConfigurationDataStore(): DataStore<Preferences>
