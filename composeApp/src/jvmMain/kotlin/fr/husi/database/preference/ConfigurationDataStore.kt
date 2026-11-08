package fr.husi.database.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import fr.husi.repository.resolveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File

internal expect fun createPlatformConfigurationDataStore(scope: CoroutineScope): DataStore<Preferences>

internal expect fun createSimpleConfigurationDataStore(
    file: File,
    scope: CoroutineScope,
): DataStore<Preferences>

internal fun preferenceStoreScope(): CoroutineScope = CoroutineScope(
    resolveRepository().preferenceStoreDispatcher + SupervisorJob(),
)
