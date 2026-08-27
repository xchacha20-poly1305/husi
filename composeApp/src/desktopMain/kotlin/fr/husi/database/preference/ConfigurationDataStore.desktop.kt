package fr.husi.database.preference

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import fr.husi.repository.resolveRepository
import io.github.vinceglb.filekit.absolutePath
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

internal actual fun createConfigurationDataStore(scope: CoroutineScope): DataStore<Preferences> {
    val file = resolveRepository().resolveDatabaseFile("configuration.preferences_pb")
    return PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { file.absolutePath().toPath() },
    )
}
