package fr.husi.ktx

import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.exists
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.net.URI

fun openUri(uri: String): String? {
    return try {
        if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(URI(uri))
                return null
            }
        }
        val command = when (PlatformInfo.platform) {
            Platform.MacOs -> listOf("open", uri)
            Platform.Windows -> listOf("rundll32", "url.dll,FileProtocolHandler", uri)
            Platform.Linux -> listOf("xdg-open", uri)
            else -> error("impossible")
        }
        ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        null
    } catch (e: Exception) {
        e.readableMessage
    }
}

fun openFilePath(path: String) {
    val cleanPath = path.trim()
    if (cleanPath.isBlank()) return

    val file = PlatformFile(cleanPath)
    if (!file.exists()) return

    try {
        // https://bugs.openjdk.org/browse/JDK-8233994
        if (PlatformInfo.isWindows) {
            val windowsPath = file.nativeCanonicalPath()
            // use cmd.exe to handle edge cases
            ProcessBuilder("cmd.exe", "/c", "explorer.exe /select,\"$windowsPath\"").start()
        } else {
            browseFileDirectory(file)
        }
    } catch (e: Exception) {
        Logs.e("select file", e)
    }
}
