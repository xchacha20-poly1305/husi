package fr.husi.repository

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.io.path.createTempDirectory

internal fun stubResourceString(key: String, formatArgs: Array<out Any>): String {
    if (formatArgs.isEmpty()) return key
    return formatArgs.joinToString(prefix = "$key(", postfix = ")")
}

class FakeRepository : LocalRepository {
    override val isMainProcess = true
    override val isBgProcess = false
    override val isTv = false

    override val preferenceStoreDispatcher = Dispatchers.Unconfined

    @Suppress("NewApi")
    private val tempRoot = PlatformFile(createTempDirectory("husi-fake-repo").toString())
    override val cacheDir = tempRoot.resolve("cache").apply { createDirectories() }
    override val filesDir = tempRoot.resolve("files").apply { createDirectories() }
    override val externalAssetsDir = tempRoot.resolve("external").apply { createDirectories() }
    override fun resolveDatabaseFile(name: String): PlatformFile {
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
