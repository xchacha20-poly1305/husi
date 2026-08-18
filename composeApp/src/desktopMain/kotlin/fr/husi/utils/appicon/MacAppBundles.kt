package fr.husi.utils.appicon

import fr.husi.ktx.blankAsNull
import fr.husi.ui.dashboard.ProcessInfo
import java.io.File

internal object MacAppBundles {
    private const val DISPLAY_NAME_KEY = "CFBundleDisplayName"
    private const val BUNDLE_NAME_KEY = "CFBundleName"
    private const val ICON_FILE_KEY = "CFBundleIconFile"

    fun resolve(executablePath: String): ProcessInfo? {
        val appDir = findAppBundle(File(executablePath)) ?: return null
        val contentsDir = File(appDir, "Contents")
        val resourcesDir = File(contentsDir, "Resources")
        val infoPlist = File(contentsDir, "Info.plist")

        val parsed = if (infoPlist.isFile) readBundleInfo(infoPlist) else null

        val label = parsed?.displayName.blankAsNull()
            ?: parsed?.bundleName.blankAsNull()
            ?: appDir.name.let { name ->
                if (name.endsWith(".app", ignoreCase = true)) name.dropLast(4) else name
            }

        val iconFile = resolveIconFile(resourcesDir, parsed?.iconFile)
        val icon = iconFile?.let(AppIconDecoding::decodeFile)
        if (label.isBlank() && icon == null) return null
        return ProcessInfo(
            packageName = executablePath,
            label = label,
            icon = icon,
        )
    }

    internal fun findAppBundle(start: File): File? {
        var current: File? = if (start.isDirectory) start else start.parentFile
        while (current != null) {
            if (current.name.endsWith(".app", ignoreCase = true) && current.isDirectory) {
                return current
            }
            current = current.parentFile
        }
        return null
    }

    internal fun readBundleInfo(infoPlist: File): MacBundleInfo {
        val values = MacPropertyList.readTopLevelStrings(
            file = infoPlist,
            keys = setOf(DISPLAY_NAME_KEY, BUNDLE_NAME_KEY, ICON_FILE_KEY),
        )
        return MacBundleInfo(
            displayName = values[DISPLAY_NAME_KEY],
            bundleName = values[BUNDLE_NAME_KEY],
            iconFile = values[ICON_FILE_KEY],
        )
    }

    private fun resolveIconFile(resourcesDir: File, iconFileName: String?): File? {
        if (!resourcesDir.isDirectory) return null
        val candidates = ArrayList<String>()
        iconFileName?.trim().blankAsNull()?.let { name ->
            candidates.add(name)
            if (!name.endsWith(".icns", ignoreCase = true)) {
                candidates.add("$name.icns")
            }
        }
        candidates.add("AppIcon.icns")
        for (name in candidates) {
            val file = File(resourcesDir, name)
            if (file.isFile) return file
        }
        val icnsFiles = resourcesDir.listFiles { file ->
            file.isFile && file.name.endsWith(".icns", ignoreCase = true)
        }
        return icnsFiles?.singleOrNull()
    }
}

internal data class MacBundleInfo(
    val displayName: String?,
    val bundleName: String?,
    val iconFile: String?,
)
