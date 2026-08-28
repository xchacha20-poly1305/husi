package fr.husi.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import fr.husi.database.preference.createPlatformConfigurationDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import java.io.File
import org.jetbrains.compose.resources.getPluralString as getComposePluralString
import org.jetbrains.compose.resources.getString as getComposeString

interface Repository {
    val isMainProcess: Boolean
    val isBgProcess: Boolean
    val isTv: Boolean

    val boxService: fr.husi.libcore.Service?

    val preferenceStoreDispatcher: CoroutineDispatcher get() = Dispatchers.IO

    fun createConfigurationDataStore(scope: CoroutineScope): DataStore<Preferences> =
        createPlatformConfigurationDataStore(scope)

    val cacheDir: File
    val filesDir: File
    val externalAssetsDir: File
    fun resolveDatabaseFile(name: String): File

    suspend fun getString(resource: StringResource): String = getComposeString(resource)

    suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
        getComposeString(resource, *formatArgs)

    suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = getComposePluralString(resource, quantity, *formatArgs)

    fun startService()
    fun reloadService()
    fun stopService()
}
