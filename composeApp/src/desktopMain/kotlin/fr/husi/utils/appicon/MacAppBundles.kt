package fr.husi.utils.appicon

import fr.husi.ktx.blankAsNull
import fr.husi.ktx.listOrEmpty
import fr.husi.ui.dashboard.ProcessInfo
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.parent

internal object MacAppBundles {
    private const val DISPLAY_NAME_KEY = "CFBundleDisplayName"
    private const val BUNDLE_NAME_KEY = "CFBundleName"
    private const val ICON_FILE_KEY = "CFBundleIconFile"

    fun resolve(executablePath: String): ProcessInfo? {
        val appDir = findAppBundle(PlatformFile(executablePath)) ?: return null
        val contentsDir = appDir / "Contents"
        val resourcesDir = contentsDir / "Resources"
        val infoPlist = contentsDir / "Info.plist"

        val parsed = if (infoPlist.isRegularFile()) readBundleInfo(infoPlist) else null

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

    internal fun findAppBundle(start: PlatformFile): PlatformFile? {
        var current: PlatformFile? = if (start.isDirectory()) start else start.parent()
        while (current != null) {
            if (current.name.endsWith(".app", ignoreCase = true) && current.isDirectory()) {
                return current
            }
            current = current.parent()
        }
        return null
    }

    internal fun readBundleInfo(infoPlist: PlatformFile): MacBundleInfo {
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

    private fun resolveIconFile(resourcesDir: PlatformFile, iconFileName: String?): PlatformFile? {
        if (!resourcesDir.isDirectory()) return null
        val candidates = ArrayList<String>()
        iconFileName?.trim().blankAsNull()?.let { name ->
            candidates.add(name)
            if (!name.endsWith(".icns", ignoreCase = true)) {
                candidates.add("$name.icns")
            }
        }
        candidates.add("AppIcon.icns")
        for (name in candidates) {
            val file = resourcesDir / name
            if (file.isRegularFile()) return file
        }
        return resourcesDir.listOrEmpty().singleOrNull { file ->
            file.isRegularFile() && file.name.endsWith(".icns", ignoreCase = true)
        }
    }
}

internal data class MacBundleInfo(
    val displayName: String?,
    val bundleName: String?,
    val iconFile: String?,
)
