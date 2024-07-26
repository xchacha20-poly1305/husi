package io.nekohasekai.sagernet.plugin

import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.plugin.PluginManager.loadString
import io.nekohasekai.sagernet.utils.PackageCache
import moe.matsuri.nb4a.utils.listByLineOrComma

object Plugins {
    const val AUTHORITIES_PREFIX_HUSI_EXE = "fr.husi.plugin."
    const val AUTHORITIES_PREFIX_SEKAI_EXE = "io.nekohasekai.sagernet.plugin." // https://github.com/SagerNet/SagerNet
    const val AUTHORITIES_PREFIX_NEKO_EXE = "moe.matsuri.exe." // https://github.com/MatsuriDayo/plugins
    const val AUTHORITIES_PREFIX_DYHKWONG = "com.github.dyhkwong."// https://github.com/dyhkwong/Exclave

    const val ACTION_NATIVE_PLUGIN = "io.nekohasekai.sagernet.plugin.ACTION_NATIVE_PLUGIN"

    const val METADATA_KEY_ID = "io.nekohasekai.sagernet.plugin.id"
    const val METADATA_KEY_EXECUTABLE_PATH = "io.nekohasekai.sagernet.plugin.executable_path"

    val allowedSet = HashSet<String>(DataStore.customPluginPrefix.listByLineOrComma()).apply {
        add(AUTHORITIES_PREFIX_HUSI_EXE)
        add(AUTHORITIES_PREFIX_SEKAI_EXE)
        add(AUTHORITIES_PREFIX_NEKO_EXE)
        add(AUTHORITIES_PREFIX_DYHKWONG)
    }

    fun isPlugin(pkg: PackageInfo): Boolean {
        if (pkg.providers.isNullOrEmpty()) return false
        val auth = pkg.providers[0].authority ?: return false
        for (prefix in allowedSet) {
            if (auth.startsWith(prefix)) return true
        }
        return false
    }

    fun preferExePrefix(): String {
        return AUTHORITIES_PREFIX_HUSI_EXE
    }

    fun displayExeProvider(pkgName: String): String {
        return when {
            pkgName.startsWith(AUTHORITIES_PREFIX_HUSI_EXE) -> SagerNet.application.getString(R.string.app_name)
            pkgName.startsWith(AUTHORITIES_PREFIX_SEKAI_EXE) -> "SagerNet"
            pkgName.startsWith(AUTHORITIES_PREFIX_DYHKWONG) -> "dyhkwong"
            pkgName.startsWith(AUTHORITIES_PREFIX_NEKO_EXE) -> "Matsuri"
            else -> "Unknown"
        }
    }

    fun getPlugin(pluginId: String): ProviderInfo? {
        if (pluginId.isBlank()) return null
        getPluginExternal(pluginId)?.let { return it }
        // internal so
        return ProviderInfo().apply { authority = AUTHORITIES_PREFIX_HUSI_EXE }
    }

    fun getPluginExternal(pluginId: String): ProviderInfo? {
        if (pluginId.isBlank()) return null

        val providers = getExtPlugin(pluginId)

        val preferProvider = providers.find {
            it.authority.startsWith(preferExePrefix())
        }
        if (preferProvider != null) return preferProvider

        return providers.randomOrNull()
    }

    private fun getExtPlugin(pluginId: String): List<ProviderInfo> {
        PackageCache.awaitLoadSync()
        val pkgs = PackageCache.installedPluginPackages
            .map { it.value }
            .filter { it.providers[0].loadString(METADATA_KEY_ID) == pluginId }
        return pkgs.map { it.providers[0] }
    }

}
