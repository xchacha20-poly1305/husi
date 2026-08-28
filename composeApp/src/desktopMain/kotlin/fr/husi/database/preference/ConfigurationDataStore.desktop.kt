package fr.husi.database.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import fr.husi.repository.resolveRepository
import kotlinx.coroutines.CoroutineScope

internal actual fun createPlatformConfigurationDataStore(scope: CoroutineScope): DataStore<Preferences> {
    val file = resolveRepository().resolveDatabaseFile("configuration.preferences_pb")
    return createSimpleConfigurationDataStore(file, scope)
}
