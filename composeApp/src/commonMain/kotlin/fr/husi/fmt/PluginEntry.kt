package fr.husi.fmt

import fr.husi.resources.Res
import fr.husi.resources.action_hysteria
import fr.husi.resources.action_hysteria2
import fr.husi.resources.action_juicity
import fr.husi.resources.action_mieru
import fr.husi.resources.action_naive
import fr.husi.resources.action_shadowquic
import org.jetbrains.compose.resources.StringResource

enum class PluginEntry(
    val pluginId: String,
    val displayName: StringResource,
    val downloadSource: DownloadSource,
) {
    MieruProxy(
        "mieru-plugin",
        Res.string.action_mieru,
        DownloadSource(
            apk = "https://github.com/xchacha20-poly1305/husi/releases?q=plugin-mieru",
            binary = "https://github.com/enfein/mieru/releases",
        ),
    ),
    NaiveProxy(
        "naive-plugin",
        Res.string.action_naive,
        DownloadSource(
            apk = "https://github.com/klzgrad/naiveproxy/releases",
            binary = "https://github.com/klzgrad/naiveproxy/releases",
        ),
    ),
    Hysteria(
        "hysteria-plugin",
        Res.string.action_hysteria,
        DownloadSource(
            apk = "https://github.com/dyhkwong/Exclave/releases?q=hysteria-plugin-1",
            binary = "https://github.com/apernet/hysteria/releases?q=v1",
        ),
    ),
    Hysteria2(
        "hysteria2-plugin",
        Res.string.action_hysteria2,
        DownloadSource(
            apk = "https://github.com/xchacha20-poly1305/husi/releases?q=plugin-hysteria2",
            binary = "https://github.com/apernet/hysteria/releases",
        ),
    ),
    Juicity(
        "juicity-plugin",
        Res.string.action_juicity,
        DownloadSource(
            apk = "https://github.com/xchacha20-poly1305/husi/releases?q=plugin-juicity",
            binary = "https://github.com/juicity/juicity/releases",
        ),
    ),
    ShadowQuic(
        "shadowquic-plugin",
        Res.string.action_shadowquic,
        DownloadSource(
            apk = "https://github.com/xchacha20-poly1305/husi/releases?q=plugin-shadowquic",
            binary = "https://github.com/spongebob888/shadowquic/releases",
        ),
    )
    ;

    fun getVersion(executable: String): String {
        val output = when (this) {
            MieruProxy -> runCommand(executable, "version")
            NaiveProxy -> runCommand(executable, "--version")
            Hysteria -> runCommand(executable, "--version")
            Hysteria2 -> runCommand(executable, "version")
            Juicity -> runCommand(executable, "--version")
            ShadowQuic -> runCommand(executable, "--version")
        }
        return when (this) {
            MieruProxy -> parseMieru(output)
            NaiveProxy -> parseNaive(output)
            Hysteria -> parseHysteria(output)
            Hysteria2 -> parseHysteria2(output)
            Juicity -> parseJuicity(output)
            ShadowQuic -> parseShadowQuic(output)
        }
    }

    data class DownloadSource(
        val apk: String = "https://github.com/xchacha20-poly1305/husi/releases",
        val binary: String,
    )

    companion object {
        fun find(name: String?): PluginEntry? {
            if (name.isNullOrBlank()) return null
            for (pluginEntry in enumValues<PluginEntry>()) {
                if (name == pluginEntry.pluginId) {
                    return pluginEntry
                }
            }
            return null
        }

    }

    // 3.27.0
    private fun parseMieru(output: String): String {
        val line = firstNonBlankLine(output) ?: return "unknown"
        return line.trim().ifEmpty { "unknown" }
    }

    // naive 143.0.7499.109
    private fun parseNaive(output: String): String {
        val line = firstNonBlankLine(output) ?: return "unknown"
        val tokens = tokenize(line)
        return if (tokens.size >= 2) tokens[1] else "unknown"
    }

    // hysteria version v1.3.5 2023-06-11 23:47:46 57c5164854d6cfe00bead730cce731da2babe406
    private fun parseHysteria(output: String): String {
        val line = firstNonBlankLine(output) ?: return "unknown"
        val tokens = tokenize(line)
        val index = tokens.indexOf("version")
        if (index >= 0 && index + 1 < tokens.size) {
            return tokens[index + 1].removePrefix("v")
        }
        return "unknown"
    }

    /*
░█░█░█░█░█▀▀░▀█▀░█▀▀░█▀▄░▀█▀░█▀█░░░▀▀▄
░█▀█░░█░░▀▀█░░█░░█▀▀░█▀▄░░█░░█▀█░░░▄▀░
░▀░▀░░▀░░▀▀▀░░▀░░▀▀▀░▀░▀░▀▀▀░▀░▀░░░▀▀▀

a powerful, lightning fast and censorship resistant proxy
Aperture Internet Laboratory <https://github.com/apernet>

Version:	v2.7.0
BuildDate:	2026-01-12T01:27:05Z
BuildType:	release
Toolchain:	go1.25.5 linux/amd64
CommitHash:	44a5643535bf63760659b2a8c76fde6330792ab8
Platform:	linux
Architecture:	amd64
Libraries:	quic-go=v0.57.2-0.20260111184307-eec823306178
     */
    private fun parseHysteria2(output: String): String {
        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Version:")) {
                val version = trimmed.substringAfter("Version:").trim()
                if (version.isNotEmpty()) return version.removePrefix("v")
            }
        }
        return "unknown"
    }

    /*
juicity-client version v0.5.0
go runtime go1.24.4 linux/amd64
CGO_ENABLED: 0
Copyright (c) 2023 juicity
License GNU AGPLv3 <https://github.com/juicity/juicity/blob/main/LICENSE>
     */
    private fun parseJuicity(output: String): String {
        val line = firstNonBlankLine(output) ?: return "unknown"
        val tokens = tokenize(line)
        val index = tokens.indexOf("version")
        if (index >= 0 && index + 1 < tokens.size) {
            return tokens[index + 1].removePrefix("v")
        }
        return "unknown"
    }

    // shadowquic 0.3.3
    private fun parseShadowQuic(output: String): String {
        val line = firstNonBlankLine(output) ?: return "unknown"
        val tokens = tokenize(line)
        return if (tokens.size >= 2) tokens[1] else "unknown"
    }

    private fun firstNonBlankLine(output: String): String? {
        for (line in output.lineSequence()) {
            if (line.isNotBlank()) return line
        }
        return null
    }

    private fun tokenize(line: String): List<String> {
        val parts = line.trim().split(' ')
        val tokens = ArrayList<String>(parts.size)
        for (part in parts) {
            if (part.isNotBlank()) tokens.add(part)
        }
        return tokens
    }

    private fun runCommand(executable: String, vararg args: String): String {
        return try {
            val command = ArrayList<String>(1 + args.size)
            command.add(executable)
            command.addAll(args)
            val process = ProcessBuilder()
                .command(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            process.waitFor()
            output.trim()
        } catch (_: Exception) {
            ""
        }
    }

}
