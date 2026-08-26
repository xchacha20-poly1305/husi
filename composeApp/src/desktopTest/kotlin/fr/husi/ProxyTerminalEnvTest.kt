package fr.husi

import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyTerminalEnvTest {
    private val http = "http://127.0.0.1:2080"
    private val socks = "socks5h://127.0.0.1:2080"

    @Test
    fun `shell is detected from the login shell path`() {
        assertEquals(
            ProxyEnvShell.Fish,
            detectProxyEnvShell(isWindows = false, shell = "/usr/bin/fish"),
        )
        assertEquals(
            ProxyEnvShell.Posix,
            detectProxyEnvShell(isWindows = false, shell = "/bin/zsh"),
        )
        assertEquals(
            ProxyEnvShell.PowerShell,
            detectProxyEnvShell(isWindows = true, shell = """C:\Program Files\PowerShell\pwsh.exe"""),
        )
        assertEquals(
            ProxyEnvShell.Cmd,
            detectProxyEnvShell(isWindows = true, shell = """C:\Windows\System32\cmd.exe"""),
        )
    }

    @Test
    fun `unset shell falls back per platform`() {
        assertEquals(ProxyEnvShell.Posix, detectProxyEnvShell(isWindows = false, shell = null))
        assertEquals(ProxyEnvShell.Posix, detectProxyEnvShell(isWindows = false, shell = "  "))
        assertEquals(ProxyEnvShell.PowerShell, detectProxyEnvShell(isWindows = true, shell = null))
        assertEquals(ProxyEnvShell.PowerShell, detectProxyEnvShell(isWindows = true, shell = ""))
    }

    @Test
    fun `posix export is one command`() {
        assertEquals(
            "export HTTP_PROXY='http://127.0.0.1:2080' http_proxy='http://127.0.0.1:2080' " +
                "HTTPS_PROXY='http://127.0.0.1:2080' https_proxy='http://127.0.0.1:2080' " +
                "ALL_PROXY='socks5h://127.0.0.1:2080' all_proxy='socks5h://127.0.0.1:2080' " +
                "NO_PROXY='localhost,127.0.0.1,::1' no_proxy='localhost,127.0.0.1,::1'",
            proxyTerminalEnvCommand(http, socks, ProxyEnvShell.Posix),
        )
    }

    @Test
    fun `fish export uses set -gx`() {
        assertEquals(
            "set -gx HTTP_PROXY 'http://127.0.0.1:2080'; set -gx http_proxy 'http://127.0.0.1:2080'; " +
                "set -gx HTTPS_PROXY 'http://127.0.0.1:2080'; set -gx https_proxy 'http://127.0.0.1:2080'; " +
                "set -gx ALL_PROXY 'socks5h://127.0.0.1:2080'; set -gx all_proxy 'socks5h://127.0.0.1:2080'; " +
                "set -gx NO_PROXY 'localhost,127.0.0.1,::1'; set -gx no_proxy 'localhost,127.0.0.1,::1'",
            proxyTerminalEnvCommand(http, socks, ProxyEnvShell.Fish),
        )
    }

    @Test
    fun `powershell export assigns env drive entries`() {
        val command = proxyTerminalEnvCommand(http, socks, ProxyEnvShell.PowerShell)

        assertEquals(
            $$"""$env:HTTP_PROXY='http://127.0.0.1:2080'""",
            command.substringBefore("; "),
        )
        assertEquals(
            $$"""$env:no_proxy='localhost,127.0.0.1,::1'""",
            command.substringAfterLast("; "),
        )
    }

    @Test
    fun `cmd export leaves the values unquoted`() {
        val command = proxyTerminalEnvCommand(http, socks, ProxyEnvShell.Cmd)

        assertEquals("set HTTP_PROXY=http://127.0.0.1:2080", command.substringBefore(" && "))
        assertEquals("set no_proxy=localhost,127.0.0.1,::1", command.substringAfterLast(" && "))
    }

    @Test
    fun `credentials are carried by the URL`() {
        val authenticated = "http://user:pass@127.0.0.1:2080"

        assertEquals(
            "export HTTP_PROXY='http://user:pass@127.0.0.1:2080' " +
                "http_proxy='http://user:pass@127.0.0.1:2080' " +
                "HTTPS_PROXY='http://user:pass@127.0.0.1:2080' " +
                "https_proxy='http://user:pass@127.0.0.1:2080' " +
                "ALL_PROXY='socks5h://127.0.0.1:2080' all_proxy='socks5h://127.0.0.1:2080' " +
                "NO_PROXY='localhost,127.0.0.1,::1' no_proxy='localhost,127.0.0.1,::1'",
            proxyTerminalEnvCommand(authenticated, socks, ProxyEnvShell.Posix),
        )
    }

    @Test
    fun `single quotes leave the quoting instead of ending it`() {
        // Libcore escapes them inside userinfo, but the quoting must not rely on that.
        val quoted = "http://user:pa'ss@127.0.0.1:2080"

        assertEquals(
            """'http://user:pa'\''ss@127.0.0.1:2080'""",
            proxyTerminalEnvCommand(quoted, socks, ProxyEnvShell.Posix)
                .substringAfter("HTTP_PROXY=")
                .substringBefore(" http_proxy="),
        )
        assertEquals(
            """'http://user:pa''ss@127.0.0.1:2080'""",
            proxyTerminalEnvCommand(quoted, socks, ProxyEnvShell.PowerShell)
                .substringAfter("HTTP_PROXY=")
                .substringBefore("; "),
        )
    }
}
