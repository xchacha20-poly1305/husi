package fr.husi.ktx

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.atomicMove
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.list
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.readString
import java.util.UUID

/**
 * Absolute path with `/` separators, for anything handed to the Go core or stored
 * as a portable path string. Windows `\` is rewritten.
 */
fun PlatformFile.invariantPathString(): String = absolutePath().replace('\\', '/')

/**
 * [invariantPathString] with a trailing `/`, used as a directory prefix (socket
 * base paths, geo resource roots).
 */
fun PlatformFile.invariantDirectoryPathString(): String = invariantPathString().trimEnd('/') + "/"

/**
 * Deletes this file or directory tree. Missing paths are ignored: FileKit's
 * [delete] defaults to `mustExist = true` and throws, which is the opposite of
 * `java.io.File.delete()`.
 *
 * FileKit's own [delete] only removes empty directories, so this walks children
 * first.
 */
suspend fun PlatformFile.deleteRecursively() {
    if (isDirectory()) {
        for (child in listOrEmpty()) {
            child.deleteRecursively()
        }
    }
    delete(mustExist = false)
}

/**
 * Deletes this path if it exists. Prefer this over [delete] whenever the
 * caller used `File.delete()` and did not require the file to be present.
 */
suspend fun PlatformFile.deleteIfExists() {
    delete(mustExist = false)
}

/**
 * Children of this directory, or an empty list when the path is missing, not a
 * directory, or cannot be listed. Replaces `File.listFiles()`'s null return.
 */
fun PlatformFile.listOrEmpty(): List<PlatformFile> {
    if (!isDirectory()) return emptyList()
    return runCatching { list() }.getOrDefault(emptyList())
}

/**
 * A unique child path under this directory. The parent is created; the child
 * file itself is not. Callers that previously relied on `File.deleteOnExit`
 * must delete the result themselves.
 *
 * @param prefix Name prefix, typically ending without a separator.
 * @param suffix Name suffix, including a leading `.` when it is an extension.
 */
fun PlatformFile.createTempChild(prefix: String, suffix: String): PlatformFile {
    createDirectories()
    return this / "$prefix-${UUID.randomUUID()}$suffix"
}

/**
 * UTF-8 contents, or `null` when the file cannot be read.
 */
suspend fun PlatformFile.readStringOrNull(): String? = runCatching { readString() }.getOrNull()

/**
 * Copies this file or directory tree onto [destination]. Destination directories
 * are created; existing files at the same path are overwritten via FileKit
 * [copyTo].
 */
suspend fun PlatformFile.copyRecursivelyTo(destination: PlatformFile) {
    if (isDirectory()) {
        destination.createDirectories()
        for (child in listOrEmpty()) {
            child.copyRecursivelyTo(destination / child.name)
        }
        return
    }
    destination.parent()?.createDirectories()
    copyTo(destination)
}

/**
 * Moves this path to [destination], using FileKit [atomicMove] and falling
 * back to a recursive copy plus delete when the rename cannot be atomic
 * (cross-device).
 */
suspend fun PlatformFile.atomicMoveOrCopy(destination: PlatformFile) {
    destination.parent()?.createDirectories()
    try {
        atomicMove(destination)
    } catch (_: Exception) {
        copyRecursivelyTo(destination)
        deleteRecursively()
    }
}
