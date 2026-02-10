package fr.husi.ktx

import fr.husi.repository.repo
import java.awt.Desktop
import java.io.File

fun openFilePath(path: String) {
    val cleanPath = path.trim()
    if (cleanPath.isBlank()) return

    val file = File(cleanPath)
    if (!file.exists()) return

    try {
        // https://bugs.openjdk.org/browse/JDK-8233994
        if (repo.isWindows) {
            val windowsPath = file.canonicalPath.replace("/", "\\")
            // use cmd.exe to handle edge cases
            ProcessBuilder("cmd.exe", "/c", "explorer.exe /select,\"$windowsPath\"").start()
        } else {
            Desktop.getDesktop().browseFileDirectory(file.absoluteFile)
        }
    } catch (e: Exception) {
        Logs.e("select file", e)
    }
}