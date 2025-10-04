package io.nekohasekai.sagernet.repository

import android.content.Context
import java.io.File

class TempRepository(
    context: Context,
    override val isMainProcess: Boolean = true,
    override val isBgProcess: Boolean = false,
) : SagerRepository(context, isMainProcess, isBgProcess) {

    private val tempRoot: File by lazy {
        createTempDir("husi-temp-repo")
    }

    override val cacheDir: File by lazy {
        tempRoot.resolve("cache").apply { mkdirs() }
    }

    override val filesDir: File by lazy {
        tempRoot.resolve("files").apply { mkdirs() }
    }

    override val externalAssetsDir: File by lazy {
        tempRoot.resolve("external").apply { mkdirs() }
    }

    override val noBackupFilesDir: File by lazy {
        tempRoot.resolve("no_backup").apply { mkdirs() }
    }

    override fun getDatabasePath(name: String): File {
        return tempRoot.resolve("database").apply { mkdirs() }.resolve(name)
    }

    private fun createTempDir(prefix: String): File {
        val tempFile = File.createTempFile(prefix, "")
        if (!tempFile.delete()) {
            throw IllegalStateException("Could not delete temp file ${tempFile.absolutePath}")
        }
        if (!tempFile.mkdirs()) {
            throw IllegalStateException("Could not create temp dir ${tempFile.absolutePath}")
        }
        tempFile.deleteOnExit()
        return tempFile
    }

}
