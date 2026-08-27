package fr.husi.ui

import fr.husi.di.initHusiKoin
import fr.husi.libcore.Service
import fr.husi.repository.Repository
import kotlinx.coroutines.Dispatchers
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.io.path.createTempDirectory

private class PreviewRepository : Repository {
    override val isMainProcess = true
    override val isBgProcess = false
    override val isTv = false

    override val boxService: Service? = null

    override val preferenceStoreDispatcher = Dispatchers.Unconfined

    @Suppress("NewApi")
    private val tempRoot = createTempDirectory("husi-preview").toFile()
    override val cacheDir = tempRoot.resolve("cache").apply { mkdirs() }
    override val filesDir = tempRoot.resolve("files").apply { mkdirs() }
    override val externalAssetsDir = tempRoot.resolve("external").apply { mkdirs() }
    override fun resolveDatabaseFile(name: String): File {
        return tempRoot.resolve(name)
    }

    override fun startService() {}
    override fun reloadService() {}
    override fun stopService() {}
}

internal fun ensurePreviewRepository() {
    if (GlobalContext.getOrNull() == null) {
        initHusiKoin(PreviewRepository())
    }
}
