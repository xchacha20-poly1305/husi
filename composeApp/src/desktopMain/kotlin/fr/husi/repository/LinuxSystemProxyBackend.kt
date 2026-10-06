package fr.husi.repository

import java.io.File

private val PROXY_TYPES = listOf("ftp", "http", "https", "socks")

internal data class LinuxProxyTools(
    val hasGSettings: Boolean,
    val kWriteConfig: String?,
)

internal class LinuxSystemProxyBackend(
    private val runner: SystemCommandRunner = ProcessCommandRunner,
    private val findExecutable: (String) -> File? = ::resolveOnPath,
) : SystemProxyBackend {
    override fun enable(host: String, port: Int) {
        linuxProxyEnableCommands(detectTools(), host, port).forEach(runner::run)
    }

    override fun disable() {
        linuxProxyDisableCommands(detectTools()).forEach(runner::run)
    }

    private fun detectTools(): LinuxProxyTools {
        val kwriteConfigCommands = listOf("kwriteconfig5", "kwriteconfig6")
        val tools = LinuxProxyTools(
            hasGSettings = findExecutable("gsettings") != null,
            kWriteConfig = kwriteConfigCommands.firstOrNull { findExecutable(it) != null },
        )
        check(tools.hasGSettings || tools.kWriteConfig != null) {
            "unsupported desktop environment: neither gsettings nor kwriteconfig is available"
        }
        return tools
    }
}

private const val GNOME_PROXY_SCHEMA = "org.gnome.system.proxy"

private val KDE_RELOAD_PROXY_COMMAND = listOf(
    "dbus-send",
    "--type=signal",
    "/KIO/Scheduler",
    "org.kde.KIO.Scheduler.reparseSlaveConfiguration",
    "string:''",
)

internal fun linuxProxyEnableCommands(
    tools: LinuxProxyTools,
    host: String,
    port: Int,
): List<List<String>> = buildList {
    if (tools.hasGSettings) {
        add(gsettingsSet("$GNOME_PROXY_SCHEMA.http", "enabled", "true"))
        for (proxyType in PROXY_TYPES) {
            add(gsettingsSet("$GNOME_PROXY_SCHEMA.$proxyType", "host", host))
            add(gsettingsSet("$GNOME_PROXY_SCHEMA.$proxyType", "port", port.toString()))
        }
        add(gsettingsSet(GNOME_PROXY_SCHEMA, "use-same-proxy", "true"))
        add(gsettingsSet(GNOME_PROXY_SCHEMA, "mode", "manual"))
    }
    tools.kWriteConfig?.let { kWriteConfig ->
        add(kdeProxyWrite(kWriteConfig, "ProxyType", "1"))
        for (proxyType in PROXY_TYPES) {
            val scheme = if (proxyType == "socks") {
                "socks"
            } else {
                "http"
            }
            add(kdeProxyWrite(kWriteConfig, "${proxyType}Proxy", "$scheme://$host:$port"))
        }
        add(kdeProxyWrite(kWriteConfig, "Authmode", "0"))
        add(KDE_RELOAD_PROXY_COMMAND)
    }
}

internal fun linuxProxyDisableCommands(tools: LinuxProxyTools): List<List<String>> = buildList {
    if (tools.hasGSettings) {
        add(gsettingsSet(GNOME_PROXY_SCHEMA, "mode", "none"))
    }
    tools.kWriteConfig?.let { kWriteConfig ->
        add(kdeProxyWrite(kWriteConfig, "ProxyType", "0"))
        add(KDE_RELOAD_PROXY_COMMAND)
    }
}

private fun gsettingsSet(schema: String, key: String, value: String): List<String> {
    return listOf("gsettings", "set", schema, key, value)
}

private fun kdeProxyWrite(kWriteConfig: String, key: String, value: String): List<String> {
    return listOf(
        kWriteConfig,
        "--file", "kioslaverc",
        "--group", "Proxy Settings",
        "--key", key,
        value,
    )
}
