package fr.husi.repository

import fr.husi.libcore.createBoxService
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import java.io.File
import org.jetbrains.compose.resources.getPluralString as getComposePluralString
import org.jetbrains.compose.resources.getString as getComposeString

class DesktopRepository(
    dataDir: File = File(System.getProperty("user.home"), ".husi"),
    override val isMainProcess: Boolean = true,
    override val isBgProcess: Boolean = true,
) : Repository {

    private val osName = System.getProperty("os.name")?.lowercase().orEmpty()

    override val isAndroid = false
    override val isLinux = osName.contains("linux")
    override val isMacOs = osName.contains("mac")
    override val isWindows = osName.contains("win")
    override val isTv = false

    override val boxService: fr.husi.libcore.Service? by lazy {
        createBoxService(isBgProcess)
    }
    private val serviceRuntime by lazy {
        DesktopServiceRuntime(boxService)
    }

    override val cacheDir: File by lazy {
        dataDir.resolve("cache").apply { mkdirs() }
    }

    override val filesDir: File by lazy {
        dataDir.resolve("files").apply { mkdirs() }
    }

    override val externalAssetsDir: File by lazy {
        dataDir.resolve("external").apply { mkdirs() }
    }

    override suspend fun getString(resource: StringResource) = getComposeString(resource)
    override suspend fun getString(resource: StringResource, vararg formatArgs: Any) =
        getComposeString(resource, *formatArgs)

    override suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ) = getComposePluralString(resource, quantity, *formatArgs)

    override fun startService() {
        serviceRuntime.start()
    }

    override fun reloadService() {
        serviceRuntime.reload()
    }

    override fun stopService() {
        serviceRuntime.stop()
    }
}
