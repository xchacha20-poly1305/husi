package fr.husi.repository

import fr.husi.libcore.Service
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import java.io.File
import kotlin.io.path.createTempDirectory
import org.jetbrains.compose.resources.getPluralString as getComposePluralString
import org.jetbrains.compose.resources.getString as getComposeString

class FakeRepository : Repository {
    override val isMainProcess = true
    override val isBgProcess = false
    override val isTv = false

    override val boxService: Service? = null

    @Suppress("NewApi")
    private val tempRoot = createTempDirectory("husi-fake-repo").toFile()
    override val cacheDir = tempRoot.resolve("cache").apply { mkdirs() }
    override val filesDir = tempRoot.resolve("files").apply { mkdirs() }
    override val externalAssetsDir = tempRoot.resolve("external").apply { mkdirs() }
    override fun resolveDatabaseFile(name: String): File {
        return tempRoot.resolve(name)
    }

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
