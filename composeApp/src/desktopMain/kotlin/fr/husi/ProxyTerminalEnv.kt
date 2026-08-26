package fr.husi

import fr.husi.ktx.localProxyURL
import fr.husi.platform.PlatformInfo

enum class ProxyEnvShell {
    Posix,
    Fish,
    PowerShell,
    Cmd,
}

private const val NO_PROXY_HOSTS = "localhost,127.0.0.1,::1"

private const val SCHEME_HTTP = "http"
private const val SCHEME_SOCKS5_REMOTE_DNS = "socks5h"

suspend fun currentProxyEnvCommand(): String = proxyTerminalEnvCommand(
    http = localProxyURL(SCHEME_HTTP).string,
    socks = localProxyURL(SCHEME_SOCKS5_REMOTE_DNS).string,
    shell = detectProxyEnvShell(
        isWindows = PlatformInfo.isWindows,
        shell = System.getenv("SHELL"),
    ),
)

internal fun detectProxyEnvShell(
    isWindows: Boolean,
    shell: String?,
): ProxyEnvShell {
    val executable = shell
        ?.trim()
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.removeSuffix(".exe")
        ?.lowercase()
        .orEmpty()
    return when (executable) {
        "fish" -> ProxyEnvShell.Fish
        "powershell", "pwsh" -> ProxyEnvShell.PowerShell
        "cmd" -> ProxyEnvShell.Cmd
        "" -> if (isWindows) {
            ProxyEnvShell.PowerShell
        } else {
            ProxyEnvShell.Posix
        }

        else -> ProxyEnvShell.Posix
    }
}

internal fun proxyTerminalEnvCommand(
    http: String,
    socks: String,
    shell: ProxyEnvShell,
): String = when (shell) {
    ProxyEnvShell.Posix -> posixExport(http, socks)
    ProxyEnvShell.Fish -> fishExport(http, socks)
    ProxyEnvShell.PowerShell -> powershellExport(http, socks)
    ProxyEnvShell.Cmd -> cmdExport(http, socks)
}

private fun posixExport(http: String, socks: String): String {
    return "export " + listOf(
        "HTTP_PROXY=${posixSingleQuote(http)}",
        "http_proxy=${posixSingleQuote(http)}",
        "HTTPS_PROXY=${posixSingleQuote(http)}",
        "https_proxy=${posixSingleQuote(http)}",
        "ALL_PROXY=${posixSingleQuote(socks)}",
        "all_proxy=${posixSingleQuote(socks)}",
        "NO_PROXY=${posixSingleQuote(NO_PROXY_HOSTS)}",
        "no_proxy=${posixSingleQuote(NO_PROXY_HOSTS)}",
    ).joinToString(" ")
}

private fun fishExport(http: String, socks: String): String {
    return listOf(
        "set -gx HTTP_PROXY ${posixSingleQuote(http)}",
        "set -gx http_proxy ${posixSingleQuote(http)}",
        "set -gx HTTPS_PROXY ${posixSingleQuote(http)}",
        "set -gx https_proxy ${posixSingleQuote(http)}",
        "set -gx ALL_PROXY ${posixSingleQuote(socks)}",
        "set -gx all_proxy ${posixSingleQuote(socks)}",
        "set -gx NO_PROXY ${posixSingleQuote(NO_PROXY_HOSTS)}",
        "set -gx no_proxy ${posixSingleQuote(NO_PROXY_HOSTS)}",
    ).joinToString("; ")
}

private fun powershellExport(http: String, socks: String): String {
    return listOf(
        $$"$env:HTTP_PROXY=$${powerShellSingleQuote(http)}",
        $$"$env:http_proxy=$${powerShellSingleQuote(http)}",
        $$"$env:HTTPS_PROXY=$${powerShellSingleQuote(http)}",
        $$"$env:https_proxy=$${powerShellSingleQuote(http)}",
        $$"$env:ALL_PROXY=$${powerShellSingleQuote(socks)}",
        $$"$env:all_proxy=$${powerShellSingleQuote(socks)}",
        $$"$env:NO_PROXY=$${powerShellSingleQuote(NO_PROXY_HOSTS)}",
        $$"$env:no_proxy=$${powerShellSingleQuote(NO_PROXY_HOSTS)}",
    ).joinToString("; ")
}

private fun cmdExport(http: String, socks: String): String {
    // `set` treats quotes as part of the value, so leave the URLs unquoted.
    return listOf(
        "set HTTP_PROXY=$http",
        "set http_proxy=$http",
        "set HTTPS_PROXY=$http",
        "set https_proxy=$http",
        "set ALL_PROXY=$socks",
        "set all_proxy=$socks",
        "set NO_PROXY=$NO_PROXY_HOSTS",
        "set no_proxy=$NO_PROXY_HOSTS",
    ).joinToString(" && ")
}

/** A single-quoted POSIX (and fish) word: the quote itself has to leave the quoting. */
private fun posixSingleQuote(value: String): String {
    return "'" + value.replace("'", """'\''""") + "'"
}

/** A single-quoted PowerShell string, where the quote is escaped by doubling it. */
private fun powerShellSingleQuote(value: String): String {
    return "'" + value.replace("'", "''") + "'"
}
