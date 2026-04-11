package fr.husi

import fr.husi.platform.PlatformInfo
import java.io.File

internal fun buildLauncherCommand(vararg arguments: String): List<String> {
    return buildList {
        addAll(resolveLauncherCommand())
        addAll(arguments)
    }
}

internal fun quoteDesktopEntryArgument(argument: String): String {
    val escaped = buildString(argument.length) {
        for (char in argument) {
            when (char) {
                '\\', '"', '$', '`' -> {
                    append('\\')
                    append(char)
                }

                else -> append(char)
            }
        }
    }
    return "\"$escaped\""
}

internal fun quoteSystemdArgument(argument: String): String {
    if (argument.isEmpty()) return "\"\""
    if (argument.none { it.isWhitespace() || it == '"' || it == '\\' }) return argument

    val escaped = buildString(argument.length) {
        for (char in argument) {
            when (char) {
                '\\', '"' -> {
                    append('\\')
                    append(char)
                }

                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
    return "\"$escaped\""
}

internal fun quoteWindowsArgument(argument: String): String {
    if (argument.isEmpty()) return "\"\""
    if (argument.none { it == ' ' || it == '\t' || it == '"' }) return argument

    val quoted = StringBuilder(argument.length + 2)
    quoted.append('"')
    var slashCount = 0
    for (char in argument) {
        when (char) {
            '\\' -> slashCount++
            '"' -> {
                quoted.append("\\".repeat(slashCount * 2 + 1))
                quoted.append('"')
                slashCount = 0
            }

            else -> {
                if (slashCount > 0) {
                    quoted.append("\\".repeat(slashCount))
                    slashCount = 0
                }
                quoted.append(char)
            }
        }
    }
    if (slashCount > 0) {
        quoted.append("\\".repeat(slashCount * 2))
    }
    quoted.append('"')
    return quoted.toString()
}

internal fun xmlEscape(value: String): String {
    return buildString(value.length) {
        for (char in value) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }
}

/**
 * Resolve based on package path -> try resolving jpackage -> try getting from process
 */
private fun resolveLauncherCommand(): List<String> {
    resolvePackagedDesktopLauncher()
        ?.let { return listOf(it.absolutePath) }

    System.getProperty("jpackage.app-path")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf(File::isFile)
        ?.let { return listOf(it.absolutePath) }

    ProcessHandle.current().info().command().orElse(null)
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf(File::isFile)
        ?.takeIf { !it.name.lowercase().startsWith("java") }
        ?.let { return listOf(it.absolutePath) }

    error("Desktop launcher not found")
}

private fun resolvePackagedDesktopLauncher(): File? {
    val codeSource = DesktopAutoStart::class.java.protectionDomain?.codeSource?.location ?: return null
    val runtimePath = runCatching {
        File(codeSource.toURI())
    }.getOrElse {
        File(codeSource.path)
    }
    val appDir = runtimePath.parentFile
        ?.takeIf { runtimePath.isFile && it.name == "app" }
        ?: return null
    val appRoot = appDir.parentFile ?: return null
    return when {
        PlatformInfo.isLinux -> resolveSingleDesktopLauncher(File(appRoot, "bin")) {
            it.canExecute()
        }

        PlatformInfo.isMacOs -> resolveSingleDesktopLauncher(File(appRoot, "MacOS")) {
            it.canExecute()
        }

        PlatformInfo.isWindows -> resolveSingleDesktopLauncher(appRoot) {
            it.extension.equals("exe", ignoreCase = true)
        }

        else -> null
    }
}

private fun resolveSingleDesktopLauncher(directory: File, predicate: (File) -> Boolean): File? {
    val files = directory.listFiles() ?: return null
    return files.singleOrNull { it.isFile && predicate(it) }
}
