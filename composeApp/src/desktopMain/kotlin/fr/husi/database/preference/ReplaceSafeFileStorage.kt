package fr.husi.database.preference

import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Serializer
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import androidx.datastore.core.createSingleProcessCoordinator
import androidx.datastore.core.use
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

internal class ReplaceSafeFileStorage<T>(
    private val serializer: Serializer<T>,
    private val file: File,
) : Storage<T> {

    override fun createConnection(): StorageConnection<T> {
        val canonicalFile = file.canonicalFile
        return ReplaceSafeFileStorageConnection(
            path = canonicalFile.toPath(),
            serializer = serializer,
            coordinator = createSingleProcessCoordinator(canonicalFile),
        )
    }
}

private class ReplaceSafeFileStorageConnection<T>(
    private val path: Path,
    private val serializer: Serializer<T>,
    override val coordinator: InterProcessCoordinator,
) : StorageConnection<T> {

    private val scratchPath = path.resolveSibling("${path.fileName}.tmp")
    private val transactionMutex = Mutex()
    private val closed = AtomicBoolean(false)

    override suspend fun <R> readScope(block: suspend ReadScope<T>.(locked: Boolean) -> R): R {
        checkNotClosed()
        return transactionMutex.withLock {
            PathReadScope(path, serializer).use { block(it, true) }
        }
    }

    override suspend fun writeScope(block: suspend WriteScope<T>.() -> Unit) {
        checkNotClosed()
        Files.createDirectories(path.parent)
        transactionMutex.withLock {
            try {
                PathWriteScope(scratchPath, serializer).use { block(it) }
                if (Files.exists(scratchPath)) {
                    Files.move(scratchPath, path, StandardCopyOption.ATOMIC_MOVE)
                }
            } catch (e: IOException) {
                Files.deleteIfExists(scratchPath)
                throw e
            }
        }
    }

    override fun close() {
        closed.set(true)
    }

    private fun checkNotClosed() {
        check(!closed.get()) { "StorageConnection has already been disposed." }
    }
}

private open class PathReadScope<T>(
    protected val path: Path,
    protected val serializer: Serializer<T>,
) : ReadScope<T> {

    private val closed = AtomicBoolean(false)

    override suspend fun readData(): T {
        checkNotClosed()
        return try {
            Files.newInputStream(path).use { serializer.readFrom(it) }
        } catch (_: NoSuchFileException) {
            serializer.defaultValue
        }
    }

    override fun close() {
        closed.set(true)
    }

    protected fun checkNotClosed() {
        check(!closed.get()) { "This scope has already been closed." }
    }
}

private class PathWriteScope<T>(
    path: Path,
    serializer: Serializer<T>,
) : PathReadScope<T>(path, serializer), WriteScope<T> {

    override suspend fun writeData(value: T) {
        checkNotClosed()
        val bytes = ByteArrayOutputStream().also { serializer.writeTo(value, it) }.toByteArray()
        FileOutputStream(path.toFile()).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
    }
}
