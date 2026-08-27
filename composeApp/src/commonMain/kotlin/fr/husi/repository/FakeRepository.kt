package fr.husi.repository

import fr.husi.libcore.Service
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString as getComposePluralString
import org.jetbrains.compose.resources.getString as getComposeString

class FakeRepository : Repository {
    override val isMainProcess = true
    override val isBgProcess = false
    override val isTv = false

    override val boxService: Service? = null

    override val preferenceStoreDispatcher = Dispatchers.Unconfined

    @Suppress("NewApi")
    private val tempRoot = PlatformFile(createTempDirectory("husi-fake-repo").toString())
    override val cacheDir = (tempRoot / "cache").apply { createDirectories() }
    override val filesDir = (tempRoot / "files").apply { createDirectories() }
    override val externalAssetsDir = (tempRoot / "external").apply { createDirectories() }
    override fun resolveDatabaseFile(name: String): PlatformFile {
        return tempRoot / name
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
