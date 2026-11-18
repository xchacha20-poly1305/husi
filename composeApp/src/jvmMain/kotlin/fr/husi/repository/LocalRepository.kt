package fr.husi.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import fr.husi.database.preference.createPlatformConfigurationDataStore
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

interface LocalRepository : Repository {
    val isMainProcess: Boolean
    val isBgProcess: Boolean
    val isTv: Boolean

    val preferenceStoreDispatcher: CoroutineDispatcher get() = Dispatchers.IO

    fun createConfigurationDataStore(scope: CoroutineScope): DataStore<Preferences> =
        createPlatformConfigurationDataStore(scope)

    val cacheDir: PlatformFile
    val filesDir: PlatformFile
    val externalAssetsDir: PlatformFile
    fun resolveDatabaseFile(name: String): PlatformFile

    fun startService()
    fun reloadService()
    fun stopService()
}
