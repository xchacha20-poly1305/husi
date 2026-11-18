package fr.husi.database.preference

import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Serializer
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import androidx.datastore.core.createSingleProcessCoordinator
import androidx.datastore.core.use
import fr.husi.ktx.canonicalFile
import fr.husi.ktx.deleteIfExists
import fr.husi.ktx.moveAtomically
import fr.husi.ktx.openInputStreamIfExists
import fr.husi.ktx.toJavaFile
import fr.husi.ktx.writeAndSync
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal class ReplaceSafeFileStorage<T>(
    private val serializer: Serializer<T>,
    private val file: PlatformFile,
) : Storage<T> {

    override fun createConnection(): StorageConnection<T> {
        val canonicalFile = file.canonicalFile()
        return ReplaceSafeFileStorageConnection(
            file = canonicalFile,
            serializer = serializer,
            coordinator = createSingleProcessCoordinator(canonicalFile.toJavaFile()),
        )
    }
}

private class ReplaceSafeFileStorageConnection<T>(
    private val file: PlatformFile,
    private val serializer: Serializer<T>,
    override val coordinator: InterProcessCoordinator,
) : StorageConnection<T> {

    private val directory = checkNotNull(file.parent())
    private val scratchFile = directory.resolve("${file.name}.tmp")
    private val transactionMutex = Mutex()
    private val closed = AtomicBoolean(false)

    override suspend fun <R> readScope(block: suspend ReadScope<T>.(locked: Boolean) -> R): R {
        checkNotClosed()
        return transactionMutex.withLock {
            FileReadScope(file, serializer).use { block(it, true) }
        }
    }

    override suspend fun writeScope(block: suspend WriteScope<T>.() -> Unit) {
        checkNotClosed()
        directory.createDirectories()
        transactionMutex.withLock {
            try {
                FileWriteScope(scratchFile, serializer).use { block(it) }
                if (scratchFile.exists()) scratchFile.moveAtomically(file)
            } catch (e: IOException) {
                scratchFile.deleteIfExists()
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

private open class FileReadScope<T>(
    protected val file: PlatformFile,
    protected val serializer: Serializer<T>,
) : ReadScope<T> {

    private val closed = AtomicBoolean(false)

    override suspend fun readData(): T {
        checkNotClosed()
        val input = file.openInputStreamIfExists() ?: return serializer.defaultValue
        return input.use {
            serializer.readFrom(it)
        }
    }

    override fun close() {
        closed.set(true)
    }

    protected fun checkNotClosed() {
        check(!closed.get()) { "This scope has already been closed." }
    }
}

private class FileWriteScope<T>(
    file: PlatformFile,
    serializer: Serializer<T>,
) : FileReadScope<T>(file, serializer), WriteScope<T> {

    override suspend fun writeData(value: T) {
        checkNotClosed()
        val bytes = ByteArrayOutputStream().also { serializer.writeTo(value, it) }.toByteArray()
        file.writeAndSync(bytes)
    }
}
