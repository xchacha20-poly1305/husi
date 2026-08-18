package fr.husi.utils.appicon

import fr.husi.ktx.blankAsNull
import fr.husi.platform.Platform
import fr.husi.platform.PlatformInfo
import fr.husi.ui.dashboard.ProcessInfo
import java.io.File

internal object DesktopAppIcons {
    private val defaultPixmapsDir = File(File("/usr/share"), "pixmaps")

    fun resolve(processPath: String): ProcessInfo? {
        return resolve(
            processPath = processPath,
            platform = PlatformInfo.platform,
            env = System.getenv(),
            userHomeProperty = System.getProperty("user.home"),
        )
    }

    fun resolve(
        processPath: String,
        platform: Platform,
        env: Map<String, String>,
        userHomeProperty: String?,
        pixmapsDir: File = defaultPixmapsDir,
    ): ProcessInfo? {
        val path = processPath.trim().blankAsNull() ?: return null
        return when (platform) {
            Platform.Linux -> resolveLinux(path, env, userHomeProperty, pixmapsDir)
            Platform.MacOs -> MacAppBundles.resolve(path)
            Platform.Windows -> WindowsShellIcons.resolve(path)
            else -> null
        }
    }

    private fun resolveLinux(
        processPath: String,
        env: Map<String, String>,
        userHomeProperty: String?,
        pixmapsDir: File,
    ): ProcessInfo? {
        val entry = LinuxDesktopEntries.find(processPath, env, userHomeProperty) ?: return null
        val iconFile = entry.iconName?.let { iconName ->
            XdgIconTheme.findIconFile(
                iconName = iconName,
                env = env,
                userHomeProperty = userHomeProperty,
                pixmapsDir = pixmapsDir,
            )
        }
        val icon = iconFile?.let(AppIconDecoding::decodeFile)
        return ProcessInfo(
            packageName = processPath,
            label = entry.name,
            icon = icon,
        )
    }
}
