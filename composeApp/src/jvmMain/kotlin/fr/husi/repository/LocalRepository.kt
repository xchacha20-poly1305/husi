package fr.husi.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import fr.husi.database.preference.createPlatformConfigurationDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File

interface LocalRepository : Repository {
    val isMainProcess: Boolean
    val isBgProcess: Boolean
    val isTv: Boolean

    val preferenceStoreDispatcher: CoroutineDispatcher get() = Dispatchers.IO

    fun createConfigurationDataStore(scope: CoroutineScope): DataStore<Preferences> =
        createPlatformConfigurationDataStore(scope)

    val cacheDir: File
    val filesDir: File
    val externalAssetsDir: File
    fun resolveDatabaseFile(name: String): File

    fun startService()
    fun reloadService()
    fun stopService()
}
