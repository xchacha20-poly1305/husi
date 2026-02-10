package fr.husi.repository

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString as getComposePluralString
import org.jetbrains.compose.resources.getString as getComposeString
import java.io.File

class FakeRepository : Repository {
    override val isMainProcess = true
    override val isBgProcess = false
    override val isTv = false

    private val osName = System.getProperty("os.name")?.lowercase().orEmpty()
    override val isAndroid = false
    override val isLinux = osName.contains("linux")
    override val isMacOs = osName.contains("mac")
    override val isWindows = osName.contains("win")

    override val boxService: fr.husi.libcore.Service? = null

    private val tempRoot = File(System.getProperty("java.io.tmpdir"), "husi-fake-repo")
    override val cacheDir = tempRoot.resolve("cache").apply { mkdirs() }
    override val filesDir = tempRoot.resolve("files").apply { mkdirs() }
    override val externalAssetsDir = tempRoot.resolve("external").apply { mkdirs() }

    override suspend fun getString(resource: StringResource) = getComposeString(resource)
    override suspend fun getString(resource: StringResource, vararg formatArgs: Any) =
        getComposeString(resource, *formatArgs)

    override suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ) = getComposePluralString(resource, quantity, *formatArgs)

    override fun startService() {}
    override fun reloadService() {}
    override fun stopService() {}
}
