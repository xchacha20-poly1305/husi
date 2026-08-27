package fr.husi.repository

import fr.husi.libcore.Service
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import java.io.File
import kotlin.io.path.createTempDirectory

internal fun stubResourceString(key: String, formatArgs: Array<out Any>): String {
    if (formatArgs.isEmpty()) return key
    return formatArgs.joinToString(prefix = "$key(", postfix = ")")
}

class FakeRepository : Repository {
    override val isMainProcess = true
    override val isBgProcess = false
    override val isTv = false

    override val boxService: Service? = null

    override val preferenceStoreDispatcher = Dispatchers.Unconfined

    @Suppress("NewApi")
    private val tempRoot = createTempDirectory("husi-fake-repo").toFile()
    override val cacheDir = tempRoot.resolve("cache").apply { mkdirs() }
    override val filesDir = tempRoot.resolve("files").apply { mkdirs() }
    override val externalAssetsDir = tempRoot.resolve("external").apply { mkdirs() }
    override fun resolveDatabaseFile(name: String): File {
        return tempRoot.resolve(name)
    }

    override suspend fun getString(resource: StringResource) = resource.key
    override suspend fun getString(resource: StringResource, vararg formatArgs: Any) =
        stubResourceString(resource.key, formatArgs)

    override suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ) = stubResourceString("${resource.key}#$quantity", formatArgs)

    override fun startService() {}
    override fun reloadService() {}
    override fun stopService() {}
}
