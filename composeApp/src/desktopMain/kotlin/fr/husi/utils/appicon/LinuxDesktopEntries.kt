package fr.husi.utils.appicon

import fr.husi.DesktopPaths
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.canonicalFile
import fr.husi.ktx.invariantPathString
import fr.husi.ktx.listOrEmpty
import fr.husi.platform.Platform
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.isAbsolute
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.source
import kotlinx.io.buffered
import kotlinx.io.readString

internal data class LinuxDesktopEntry(
    val name: String,
    val iconName: String?,
)

internal class DesktopEntryIndex(
    private val byCanonicalPath: Map<String, LinuxDesktopEntry>,
    private val byBasename: Map<String, LinuxDesktopEntry>,
) {
    fun find(executablePath: String): LinuxDesktopEntry? {
        val file = PlatformFile(executablePath)
        val canonical = file.canonicalFile()
        byCanonicalPath[canonical.invariantPathString()]?.let { return it }
        return byBasename[canonical.name]
    }
}

internal object LinuxDesktopEntries {
    private const val DEFAULT_XDG_DATA_DIRS = "/usr/local/share:/usr/share"
    private val desktopFieldCodes = setOf("%f", "%F", "%u", "%U", "%i", "%c", "%k")

    private val indexLock = Any()
    private var cachedKey: DesktopEntryIndexKey? = null
    private var cachedIndex: DesktopEntryIndex? = null

    fun find(
        executablePath: String,
        env: Map<String, String>,
        userHomeProperty: String?,
    ): LinuxDesktopEntry? {
        return indexFor(env, userHomeProperty).find(executablePath)
    }

    fun buildIndex(
        env: Map<String, String>,
        userHomeProperty: String?,
    ): DesktopEntryIndex {
        val byCanonicalPath = linkedMapOf<String, LinuxDesktopEntry>()
        val byBasename = linkedMapOf<String, LinuxDesktopEntry>()
        for (applicationsDir in applicationSearchDirs(env, userHomeProperty)) {
            if (!applicationsDir.isDirectory()) continue
            for (file in applicationsDir.walkTopDown()) {
                if (!file.isRegularFile() || !file.name.endsWith(".desktop", ignoreCase = true)) {
                    continue
                }
                val parsed = parseDesktopEntryFile(file) ?: continue
                val entry = LinuxDesktopEntry(name = parsed.name, iconName = parsed.iconName)
                registerTryExec(parsed.tryExec, entry, byCanonicalPath, byBasename)
                registerExecToken(parsed.exec, entry, byCanonicalPath, byBasename)
            }
        }
        return DesktopEntryIndex(byCanonicalPath, byBasename)
    }

    internal fun parseDesktopEntryFile(file: PlatformFile): ParsedDesktopEntry? {
        val text = try {
            file.source().buffered().use { it.readString() }
        } catch (_: Exception) {
            return null
        }
        return parseDesktopEntryText(text)
    }

    internal fun parseDesktopEntryText(text: String): ParsedDesktopEntry? {
        var inDesktopEntry = false
        var type: String? = null
        var name: String? = null
        var iconName: String? = null
        var exec: String? = null
        var tryExec: String? = null
        var noDisplay = false

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                if (inDesktopEntry) break
                inDesktopEntry = line == "[Desktop Entry]"
                continue
            }
            if (!inDesktopEntry) continue
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            when (key) {
                "Type" -> type = value
                "Name" -> name = value
                "Icon" -> iconName = value
                "Exec" -> exec = value
                "TryExec" -> tryExec = value
                "NoDisplay" -> noDisplay = value.equals("true", ignoreCase = true)
            }
        }

        if (type != "Application") return null
        if (noDisplay) return null
        val label = name.blankAsNull() ?: return null
        return ParsedDesktopEntry(
            name = label,
            iconName = iconName.blankAsNull(),
            exec = exec,
            tryExec = tryExec,
        )
    }

    internal fun firstExecToken(exec: String): String? {
        for (token in tokenizeDesktopExec(exec)) {
            if (token in desktopFieldCodes) continue
            return token.blankAsNull()
        }
        return null
    }

    private fun indexFor(
        env: Map<String, String>,
        userHomeProperty: String?,
    ): DesktopEntryIndex {
        val key = DesktopEntryIndexKey(
            dataHome = env["XDG_DATA_HOME"],
            dataDirs = env["XDG_DATA_DIRS"],
            home = env["HOME"] ?: userHomeProperty,
        )
        synchronized(indexLock) {
            if (cachedKey == key) {
                cachedIndex?.let { return it }
            }
            val built = buildIndex(env, userHomeProperty)
            cachedKey = key
            cachedIndex = built
            return built
        }
    }

    private fun applicationSearchDirs(
        env: Map<String, String>,
        userHomeProperty: String?,
    ): List<PlatformFile> {
        val paths = DesktopPaths.resolve(Platform.Linux, env, userHomeProperty)
        val dataHome = env["XDG_DATA_HOME"]?.blankAsNull()?.let(::PlatformFile)
            ?: paths.userHomeDir.resolve(".local").resolve("share")
        val dataDirs = env["XDG_DATA_DIRS"]?.blankAsNull() ?: DEFAULT_XDG_DATA_DIRS
        val dirs = ArrayList<PlatformFile>()
        dirs.add(dataHome.resolve("applications"))
        for (part in dataDirs.split(':')) {
            val directory = part.blankAsNull()?.let(::PlatformFile) ?: continue
            dirs.add(directory.resolve("applications"))
        }
        return dirs
    }

    private fun registerTryExec(
        tryExec: String?,
        entry: LinuxDesktopEntry,
        byCanonicalPath: MutableMap<String, LinuxDesktopEntry>,
        byBasename: MutableMap<String, LinuxDesktopEntry>,
    ) {
        val value = tryExec?.trim().blankAsNull() ?: return
        registerExecutableKey(value, entry, byCanonicalPath, byBasename, overwrite = true)
    }

    private fun registerExecToken(
        exec: String?,
        entry: LinuxDesktopEntry,
        byCanonicalPath: MutableMap<String, LinuxDesktopEntry>,
        byBasename: MutableMap<String, LinuxDesktopEntry>,
    ) {
        val token = exec?.let(::firstExecToken) ?: return
        registerExecutableKey(token, entry, byCanonicalPath, byBasename, overwrite = false)
    }

    private fun registerExecutableKey(
        raw: String,
        entry: LinuxDesktopEntry,
        byCanonicalPath: MutableMap<String, LinuxDesktopEntry>,
        byBasename: MutableMap<String, LinuxDesktopEntry>,
        overwrite: Boolean,
    ) {
        val file = PlatformFile(raw)
        if (file.isAbsolute()) {
            val canonical = file.canonicalFile()
            putKey(byCanonicalPath, canonical.invariantPathString(), entry, overwrite)
            putKey(byBasename, canonical.name, entry, overwrite)
        } else {
            putKey(byBasename, file.name, entry, overwrite)
        }
    }

    private fun putKey(
        map: MutableMap<String, LinuxDesktopEntry>,
        key: String,
        entry: LinuxDesktopEntry,
        overwrite: Boolean,
    ) {
        if (overwrite) {
            map[key] = entry
        } else {
            map.putIfAbsent(key, entry)
        }
    }

    private fun tokenizeDesktopExec(value: String): List<String> {
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escape = false
        for (character in value) {
            when {
                escape -> {
                    current.append(character)
                    escape = false
                }
                character == '\\' && !inSingleQuote -> escape = true
                character == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
                character == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                character.isWhitespace() && !inSingleQuote && !inDoubleQuote -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(character)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }
}

internal data class ParsedDesktopEntry(
    val name: String,
    val iconName: String?,
    val exec: String?,
    val tryExec: String?,
)

private data class DesktopEntryIndexKey(
    val dataHome: String?,
    val dataDirs: String?,
    val home: String?,
)

private fun PlatformFile.walkTopDown(): Sequence<PlatformFile> = sequence {
    yield(this@walkTopDown)
    if (isDirectory()) {
        for (child in listOrEmpty()) {
            yieldAll(child.walkTopDown())
        }
    }
}
