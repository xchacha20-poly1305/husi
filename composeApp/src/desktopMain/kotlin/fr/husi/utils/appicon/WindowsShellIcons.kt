package fr.husi.utils.appicon

import fr.husi.ui.dashboard.ProcessInfo
import java.io.File
import javax.swing.Icon
import javax.swing.filechooser.FileSystemView

internal object WindowsShellIcons {
    private val systemPseudoPaths = setOf(":System", ":System Idle Process")

    fun resolve(
        processPath: String,
        loadSystemIcon: (File) -> Icon? = ::loadSystemIcon,
    ): ProcessInfo? {
        if (processPath in systemPseudoPaths) return null
        val file = File(processPath)
        if (!file.isFile) return null
        val name = file.name
        val label = if (name.endsWith(".exe", ignoreCase = true)) {
            name.dropLast(4)
        } else {
            name
        }
        val swingIcon = try {
            loadSystemIcon(file)
        } catch (_: Exception) {
            null
        }
        val icon = swingIcon?.let(AppIconDecoding::decodeSwingIcon)
        if (label.isBlank() && icon == null) return null
        return ProcessInfo(
            packageName = processPath,
            label = label,
            icon = icon,
        )
    }

    internal fun loadSystemIcon(file: File): Icon? {
        return FileSystemView.getFileSystemView().getSystemIcon(file, 64, 64)
    }
}
