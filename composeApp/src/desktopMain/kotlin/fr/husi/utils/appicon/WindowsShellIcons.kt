package fr.husi.utils.appicon

import fr.husi.ktx.toJavaFile
import fr.husi.ui.dashboard.ProcessInfo
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.name
import javax.swing.Icon
import javax.swing.filechooser.FileSystemView

internal object WindowsShellIcons {
    private val systemPseudoPaths = setOf(":System", ":System Idle Process")

    fun resolve(
        processPath: String,
        loadSystemIcon: (PlatformFile) -> Icon? = ::loadSystemIcon,
    ): ProcessInfo? {
        if (processPath in systemPseudoPaths) return null
        val file = PlatformFile(processPath)
        if (!file.isRegularFile()) return null
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

    internal fun loadSystemIcon(file: PlatformFile): Icon? {
        return FileSystemView.getFileSystemView().getSystemIcon(file.toJavaFile(), 64, 64)
    }
}
