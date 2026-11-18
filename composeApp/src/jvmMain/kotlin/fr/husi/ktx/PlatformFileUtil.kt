package fr.husi.ktx

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.source
import io.github.vinceglb.filekit.toKotlinxIoPath
import io.github.vinceglb.filekit.withScopedAccess
import kotlinx.io.asInputStream
import kotlinx.io.asOutputStream
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import okio.FileSystem
import okio.HashingSink
import okio.Path.Companion.toPath
import okio.blackholeSink
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.io.OutputStream

fun PlatformFile.toJavaFile(): File = File(toKotlinxIoPath().toString())

/** Resolves relative paths without following symbolic links or requiring the file to exist. */
fun PlatformFile.toAbsoluteFile(): PlatformFile = PlatformFile(absolutePath())

private fun PlatformFile.toOkioPath() = toKotlinxIoPath().toString().toPath()

fun PlatformFile.atomicReplace(destination: PlatformFile) {
    FileSystem.SYSTEM.atomicMove(toOkioPath(), destination.toOkioPath())
}

fun PlatformFile.invariantPathString(): String = absolutePath().replace('\\', '/')

fun PlatformFile.invariantDirectoryPathString(): String = invariantPathString().trimEnd('/') + "/"

fun PlatformFile.inputStream() = source().buffered().asInputStream()

fun PlatformFile.outputStream(append: Boolean = false): OutputStream {
    return sink(append).buffered().asOutputStream()
}

fun PlatformFile.readText() = inputStream().bufferedReader().use {
    it.readText()
}

fun PlatformFile.readBytesBlocking() = source().buffered().use {
    it.readByteArray()
}

fun PlatformFile.writeText(text: String) {
    outputStream().bufferedWriter().use {
        it.write(text) }
}

fun PlatformFile.asOkioSource() = inputStream().source()

fun PlatformFile.asOkioSink() = outputStream().sink()

fun PlatformFile.sha256Hex(): String = HashingSink.sha256(blackholeSink()).use { hashing ->
    asOkioSource().buffer().use { it.readAll(hashing) }
    hashing.hash.hex()
}

fun PlatformFile.listFilesOrNull(predicate: (PlatformFile) -> Boolean = { true }): List<PlatformFile>? =
    try {
        list().filter(predicate)
    } catch (_: IOException) {
        null
    }

fun PlatformFile.canonicalFile(): PlatformFile {
    return withScopedAccess {
        PlatformFile(it.toJavaFile().canonicalPath)
    }
}

fun PlatformFile.canonicalPath(): String = canonicalFile().absolutePath()

fun PlatformFile.canExecute(): Boolean = withScopedAccess {
    it.toJavaFile().canExecute()
}

fun PlatformFile.createNewFile(): Boolean = withScopedAccess {
    it.toJavaFile().createNewFile()
}

fun PlatformFile.deleteBlocking(): Boolean = withScopedAccess {
    it.toJavaFile().delete()
}

fun PlatformFile.deleteRecursivelyBlocking(): Boolean {
    return withScopedAccess {
        it.toJavaFile().deleteRecursively()
    }
}

fun PlatformFile.deleteOnExit() = toJavaFile().deleteOnExit()

fun PlatformFile.renameTo(target: PlatformFile): Boolean {
    return withScopedAccess { source ->
        target.withScopedAccess { destination ->
            source.toJavaFile().renameTo(destination.toJavaFile())
        }
    }
}

fun createPlatformTempFile(prefix: String, suffix: String, directory: PlatformFile? = null): PlatformFile {
    return PlatformFile(File.createTempFile(prefix, suffix, directory?.toJavaFile()).path)
}

fun PlatformFile.copyNewFileTo(destination: PlatformFile) {
    inputStream().use { input ->
        if (!destination.createNewFile()) {
            throw IOException("File already exists: $destination")
        }
        destination.outputStream().use {
            input.copyTo(it)
        }
    }
}
