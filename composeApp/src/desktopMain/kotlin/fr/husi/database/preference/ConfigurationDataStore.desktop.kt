package fr.husi.database.preference

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.emptyPreferences
import fr.husi.repository.resolveRepository
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope

internal actual fun createPlatformConfigurationDataStore(scope: CoroutineScope): DataStore<Preferences> {
    val file = resolveRepository().resolveDatabaseFile("configuration.preferences_pb")
    return createSimpleConfigurationDataStore(file, scope)
}

internal actual fun createSimpleConfigurationDataStore(
    file: PlatformFile,
    scope: CoroutineScope,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    storage = ReplaceSafeFileStorage(PreferencesFileSerializer, file),
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = scope,
)
