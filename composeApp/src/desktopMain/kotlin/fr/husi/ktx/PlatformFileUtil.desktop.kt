package fr.husi.ktx

import io.github.vinceglb.filekit.PlatformFile
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

fun PlatformFile.toNioPath(): Path = toJavaFile().toPath()
fun PlatformFile.toJavaUri(): URI = toJavaFile().toURI()

fun PlatformFile.openInputStreamIfExists(): InputStream? {
    return try {
        Files.newInputStream(toNioPath())
    } catch (_: NoSuchFileException) {
        null
    }
}

fun PlatformFile.writeAndSync(bytes: ByteArray) {
    FileOutputStream(toJavaFile()).use { stream ->
        stream.write(bytes)
        stream.fd.sync()
    }
}

fun PlatformFile.moveAtomically(destination: PlatformFile) {
    Files.move(toNioPath(), destination.toNioPath(), StandardCopyOption.ATOMIC_MOVE)
}

fun PlatformFile.moveReplacing(destination: PlatformFile) {
    Files.move(toNioPath(), destination.toNioPath(), StandardCopyOption.REPLACE_EXISTING)
}

fun PlatformFile.deleteIfExists() {
    Files.deleteIfExists(toNioPath())
}

/** Desktop entry search follows symlinked directories and skips unreadable subtrees. */
fun PlatformFile.walkFollowingDirectoryLinks(): Sequence<PlatformFile> {
    return toJavaFile().walkTopDown().map(::PlatformFile)
}
