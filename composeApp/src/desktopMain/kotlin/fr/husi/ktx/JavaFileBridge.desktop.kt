package fr.husi.ktx

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import java.awt.Desktop
import java.io.File
import java.io.RandomAccessFile
import java.net.URL
import javax.swing.Icon
import javax.swing.filechooser.FileSystemView

fun PlatformFile.toJavaFile(): File = file

fun initializeFileKit(
    appId: String,
    filesDir: PlatformFile? = null,
    cacheDir: PlatformFile? = null,
) {
    FileKit.init(appId, filesDir?.toJavaFile(), cacheDir?.toJavaFile())
}

fun PlatformFile.canExecute(): Boolean = toJavaFile().canExecute()

fun PlatformFile.setExecutable(executable: Boolean, ownerOnly: Boolean = true): Boolean {
    return toJavaFile().setExecutable(executable, ownerOnly)
}

fun ProcessBuilder.directory(directory: PlatformFile): ProcessBuilder {
    return directory(directory.toJavaFile())
}

fun browseFileDirectory(file: PlatformFile) {
    Desktop.getDesktop().browseFileDirectory(file.toJavaFile().absoluteFile)
}

fun PlatformFile.isExclusiveLockHeldByAnotherProcess(): Boolean {
    val javaFile = toJavaFile()
    if (!javaFile.isFile) return false
    return try {
        RandomAccessFile(javaFile, "rw").use { randomAccessFile ->
            val lock = randomAccessFile.channel.tryLock()
            lock?.release()
            lock == null
        }
    } catch (_: Exception) {
        false
    }
}

fun platformFilesFromAwtFileList(data: Any?): List<PlatformFile> {
    val files = data as? List<*> ?: return emptyList()
    return files.mapNotNull { item ->
        (item as? File)?.let { PlatformFile(it) }
    }
}

fun PlatformFile.canonicalFile(): PlatformFile {
    val javaFile = toJavaFile()
    val resolved = runCatching { javaFile.canonicalFile }.getOrElse { javaFile.absoluteFile }
    return PlatformFile(resolved)
}

fun PlatformFile.nativeCanonicalPath(): String {
    return toJavaFile().canonicalPath.replace("/", "\\")
}

fun platformFileFromUrl(url: URL): PlatformFile {
    val javaFile = runCatching { File(url.toURI()) }.getOrElse { File(url.path) }
    return PlatformFile(javaFile)
}

fun PlatformFile.windowsSystemIcon(width: Int = 64, height: Int = 64): Icon? {
    return FileSystemView.getFileSystemView().getSystemIcon(toJavaFile(), width, height)
}
