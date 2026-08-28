package fr.husi.database.preference

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import fr.husi.repository.resolveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath
import java.io.File

internal expect fun createPlatformConfigurationDataStore(scope: CoroutineScope): DataStore<Preferences>

internal fun createSimpleConfigurationDataStore(
    file: File,
    scope: CoroutineScope,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = scope,
    produceFile = { file.absolutePath.toPath() },
)

internal fun preferenceStoreScope(): CoroutineScope = CoroutineScope(
    resolveRepository().preferenceStoreDispatcher + SupervisorJob(),
)
