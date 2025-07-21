package io.nekohasekai.sagernet.fmt

import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet.Companion.app

enum class PluginEntry(
    val pluginId: String,
    val displayName: String,
    val packageName: String, // for f-droid page
    val downloadSource: DownloadSource = DownloadSource()
) {
    MieruProxy(
        "mieru-plugin",
        app.getStringCompat(R.string.action_mieru),
        "fr.husi.plugin.mieru",
        DownloadSource(
            fdroid = false,
            downloadLink = "https://github.com/xchacha20-poly1305/husi/releases?q=plugin-mieru",
        ),
    ),
    NaiveProxy(
        "naive-plugin",
        app.getStringCompat(R.string.action_naive),
        "fr.husi.plugin.naive",
        DownloadSource(
            fdroid = false,
            downloadLink = "https://github.com/klzgrad/naiveproxy/releases",
        ),
    ),
    Hysteria(
        "hysteria-plugin",
        app.getStringCompat(R.string.action_hysteria),
        "moe.matsuri.exe.hysteria",
        DownloadSource(
            fdroid = false,
            downloadLink = "https://github.com/dyhkwong/Exclave/releases?q=hysteria-plugin-1"
        ),
    ),
    Hysteria2(
        "hysteria2-plugin",
        app.getStringCompat(R.string.action_hysteria) + "2",
        "fr.husi.plugin.hysteria2",
        DownloadSource(
            fdroid = false,
            downloadLink = "https://github.com/xchacha20-poly1305/husi/releases?q=plugin-hysteria2",
        ),
    ),
    Juicity(
        "juicity-plugin",
        app.getStringCompat(R.string.action_juicity),
        "fr.husi.plugin.juicity",
        DownloadSource(
            fdroid = false,
            downloadLink = "https://github.com/xchacha20-poly1305/husi/releases?q=plugin-juicity",
        ),
    ),
    ShadowQuic(
        "shadowquic-plugin",
        app.getStringCompat(R.string.action_shadowquic),
        "fr.husi.plugin.shadowquic",
        DownloadSource(
            fdroid = false,
            downloadLink = "https://github.com/xchacha20-poly1305/husi/releases?q=plugin-shadowquic",
        ),
    )
    ;

    data class DownloadSource(
        val fdroid: Boolean = true,
        val downloadLink: String = "https://github.com/xchacha20-poly1305/husi/releases",
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

}
