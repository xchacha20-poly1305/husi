package fr.husi.repository

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

interface Repository {
    val isMainProcess: Boolean
    val isBgProcess: Boolean
    val isTv: Boolean

    val boxService: fr.husi.libcore.Service?

    val preferenceStoreDispatcher: CoroutineDispatcher get() = Dispatchers.IO

    val cacheDir: PlatformFile
    val filesDir: PlatformFile
    val externalAssetsDir: PlatformFile
    fun resolveDatabaseFile(name: String): PlatformFile

    suspend fun getString(resource: StringResource): String
    suspend fun getString(resource: StringResource, vararg formatArgs: Any): String
    suspend fun getPluralString(resource: PluralStringResource, quantity: Int, vararg formatArgs: Any): String

    fun startService()
    fun reloadService()
    fun stopService()
}
