package fr.husi.utils.appicon

import fr.husi.DesktopPaths
import fr.husi.ktx.blankAsNull
import fr.husi.platform.Platform
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.isAbsolute
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.source
import kotlinx.io.buffered
import kotlinx.io.readString

internal object XdgIconTheme {
    private const val DEFAULT_XDG_DATA_DIRS = "/usr/local/share:/usr/share"
    private const val DEFAULT_THEME = "hicolor"
    private val sizeDirectories = listOf("256x256", "128x128", "scalable", "64x64", "48x48")
    private val iconExtensions = listOf("png", "svg", "xpm")

    fun findIconFile(
        iconName: String,
        env: Map<String, String>,
        userHomeProperty: String?,
        pixmapsDir: PlatformFile,
    ): PlatformFile? {
        val name = iconName.trim().blankAsNull() ?: return null
        val asFile = PlatformFile(name)
        if (asFile.isAbsolute() && asFile.isRegularFile()) {
            return asFile
        }

        val themeName = currentThemeName(env, userHomeProperty) ?: DEFAULT_THEME
        for (theme in themeInheritanceChain(themeName, env, userHomeProperty)) {
            findInTheme(name, theme, env, userHomeProperty)?.let { return it }
        }

        for (extension in iconExtensions) {
            val pixmap = pixmapsDir.resolve("$name.$extension")
            if (pixmap.isRegularFile()) return pixmap
        }
        return null
    }

    internal fun currentThemeName(
        env: Map<String, String>,
        userHomeProperty: String?,
    ): String? {
        val configHome = DesktopPaths.resolve(Platform.Linux, env, userHomeProperty).configHomeDir
        for (gtkDir in listOf("gtk-4.0", "gtk-3.0")) {
            val settings = configHome.resolve(gtkDir).resolve("settings.ini")
            readGtkIconThemeName(settings)?.let { return it }
        }
        return null
    }

    internal fun readGtkIconThemeName(file: PlatformFile): String? {
        if (!file.isRegularFile()) return null
        var inSettings = false
        val lines = try {
            file.source().buffered().use { it.readString() }.lineSequence()
        } catch (_: Exception) {
            return null
        }
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                inSettings = line == "[Settings]"
                continue
            }
            if (!inSettings) continue
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim()
            if (key != "gtk-icon-theme-name") continue
            return line.substring(separator + 1).trim().trim('"', '\'').blankAsNull()
        }
        return null
    }

    internal fun themeInheritanceChain(
        startTheme: String,
        env: Map<String, String>,
        userHomeProperty: String?,
    ): List<String> {
        val result = ArrayList<String>()
        val seen = LinkedHashSet<String>()
        val pending = ArrayDeque<String>()
        pending.add(startTheme)
        while (pending.isNotEmpty()) {
            val theme = pending.removeFirst()
            if (!seen.add(theme)) continue
            result.add(theme)
            for (parent in readInherits(theme, env, userHomeProperty)) {
                if (parent !in seen) {
                    pending.add(parent)
                }
            }
        }
        if (DEFAULT_THEME !in seen) {
            result.add(DEFAULT_THEME)
        }
        return result
    }

    private fun findInTheme(
        iconName: String,
        theme: String,
        env: Map<String, String>,
        userHomeProperty: String?,
    ): PlatformFile? {
        val names = iconFileNames(iconName)
        for (size in sizeDirectories) {
            for (root in themeRoots(theme, env, userHomeProperty)) {
                val appsDir = root.resolve(size).resolve("apps")
                for (fileName in names) {
                    val file = appsDir.resolve(fileName)
                    if (file.isRegularFile()) return file
                }
            }
        }
        return null
    }

    private fun iconFileNames(iconName: String): List<String> {
        val names = ArrayList<String>(1 + iconExtensions.size)
        names.add(iconName)
        for (extension in iconExtensions) {
            val withExtension = "$iconName.$extension"
            if (withExtension != iconName) {
                names.add(withExtension)
            }
        }
        return names
    }

    private fun themeRoots(
        theme: String,
        env: Map<String, String>,
        userHomeProperty: String?,
    ): List<PlatformFile> {
        val paths = DesktopPaths.resolve(Platform.Linux, env, userHomeProperty)
        val roots = ArrayList<PlatformFile>()
        roots.add(paths.userHomeDir.resolve(".icons").resolve(theme))
        val dataDirs = env["XDG_DATA_DIRS"]?.blankAsNull() ?: DEFAULT_XDG_DATA_DIRS
        for (part in dataDirs.split(':')) {
            val directory = part.blankAsNull()?.let(::PlatformFile) ?: continue
            roots.add(directory.resolve("icons").resolve(theme))
        }
        return roots
    }

    private fun readInherits(
        theme: String,
        env: Map<String, String>,
        userHomeProperty: String?,
    ): List<String> {
        for (root in themeRoots(theme, env, userHomeProperty)) {
            val index = root.resolve("index.theme")
            val inherits = readInheritsFromIndex(index)
            if (inherits != null) return inherits
        }
        return emptyList()
    }

    private fun readInheritsFromIndex(file: PlatformFile): List<String>? {
        if (!file.isRegularFile()) return null
        var inIconTheme = false
        val lines = try {
            file.source().buffered().use { it.readString() }.lineSequence()
        } catch (_: Exception) {
            return null
        }
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                if (inIconTheme) break
                inIconTheme = line == "[Icon Theme]"
                continue
            }
            if (!inIconTheme) continue
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim()
            if (key != "Inherits") continue
            return line.substring(separator + 1)
                .split(',')
                .mapNotNull { it.trim().blankAsNull() }
        }
        return emptyList()
    }
}
