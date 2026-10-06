package fr.husi.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LinuxSystemProxyBackendTest {

    @Test
    fun `enable on GNOME sets every proxy type and switches to manual mode`() {
        val commands = linuxProxyEnableCommands(
            LinuxProxyTools(hasGSettings = true, kWriteConfig = null),
            host = "127.0.0.1",
            port = 2080,
        )

        assertEquals(
            listOf(
                listOf("gsettings", "set", "org.gnome.system.proxy.http", "enabled", "true"),
                listOf("gsettings", "set", "org.gnome.system.proxy.ftp", "host", "127.0.0.1"),
                listOf("gsettings", "set", "org.gnome.system.proxy.ftp", "port", "2080"),
                listOf("gsettings", "set", "org.gnome.system.proxy.http", "host", "127.0.0.1"),
                listOf("gsettings", "set", "org.gnome.system.proxy.http", "port", "2080"),
                listOf("gsettings", "set", "org.gnome.system.proxy.https", "host", "127.0.0.1"),
                listOf("gsettings", "set", "org.gnome.system.proxy.https", "port", "2080"),
                listOf("gsettings", "set", "org.gnome.system.proxy.socks", "host", "127.0.0.1"),
                listOf("gsettings", "set", "org.gnome.system.proxy.socks", "port", "2080"),
                listOf("gsettings", "set", "org.gnome.system.proxy", "use-same-proxy", "true"),
                listOf("gsettings", "set", "org.gnome.system.proxy", "mode", "manual"),
            ),
            commands,
        )
    }

    @Test
    fun `enable on KDE writes proxy URLs and asks KIO to reload`() {
        val commands = linuxProxyEnableCommands(
            LinuxProxyTools(hasGSettings = false, kWriteConfig = "kwriteconfig6"),
            host = "127.0.0.1",
            port = 2080,
        )

        val kdePrefix = listOf("kwriteconfig6", "--file", "kioslaverc", "--group", "Proxy Settings", "--key")
        assertEquals(
            listOf(
                kdePrefix + listOf("ProxyType", "1"),
                kdePrefix + listOf("ftpProxy", "http://127.0.0.1:2080"),
                kdePrefix + listOf("httpProxy", "http://127.0.0.1:2080"),
                kdePrefix + listOf("httpsProxy", "http://127.0.0.1:2080"),
                kdePrefix + listOf("socksProxy", "socks://127.0.0.1:2080"),
                kdePrefix + listOf("Authmode", "0"),
            ),
            commands.dropLast(1),
        )
        assertEquals("dbus-send", commands.last().first())
    }

    @Test
    fun `disable resets both desktops when both tools exist`() {
        val commands = linuxProxyDisableCommands(
            LinuxProxyTools(hasGSettings = true, kWriteConfig = "kwriteconfig5"),
        )

        assertEquals(listOf("gsettings", "set", "org.gnome.system.proxy", "mode", "none"), commands[0])
        assertEquals(
            listOf("kwriteconfig5", "--file", "kioslaverc", "--group", "Proxy Settings", "--key", "ProxyType", "0"),
            commands[1],
        )
        assertEquals("dbus-send", commands[2].first())
        assertEquals(3, commands.size)
    }

    @Test
    fun `backend prefers kwriteconfig5 and runs commands in order`() {
        val commands = mutableListOf<List<String>>()
        val backend = LinuxSystemProxyBackend(
            runner = { command -> commands += command; "" },
            findExecutable = { name -> File(name).takeIf { name.startsWith("kwriteconfig") } },
        )

        backend.disable()

        assertTrue(commands.isNotEmpty())
        assertEquals("kwriteconfig5", commands.first().first())
    }

    @Test
    fun `backend fails without any supported settings tool`() {
        val backend = LinuxSystemProxyBackend(
            runner = { error("no command should run") },
            findExecutable = { null },
        )

        assertFailsWith<IllegalStateException> { backend.enable("127.0.0.1", 2080) }
    }
}
