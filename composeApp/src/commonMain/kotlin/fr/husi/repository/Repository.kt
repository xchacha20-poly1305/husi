package fr.husi.repository

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import java.io.File

lateinit var repo: Repository

interface Repository {
    val isMainProcess: Boolean
    val isBgProcess: Boolean
    val isTv: Boolean

    val isAndroid: Boolean
    val isLinux: Boolean
    val isMacOs: Boolean
    val isWindows: Boolean

    val boxService: fr.husi.libcore.Service?

    val cacheDir: File
    val filesDir: File
    val externalAssetsDir: File

    suspend fun getString(resource: StringResource): String
    suspend fun getString(resource: StringResource, vararg formatArgs: Any): String
    suspend fun getPluralString(resource: PluralStringResource, quantity: Int, vararg formatArgs: Any): String

    fun startService()
    fun reloadService()
    fun stopService()
}
