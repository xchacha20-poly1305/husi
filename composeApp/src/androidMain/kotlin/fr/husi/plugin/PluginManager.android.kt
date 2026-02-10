package fr.husi.plugin

import android.content.pm.ProviderInfo
import fr.husi.ktx.Logs
import fr.husi.repository.androidRepo
import java.io.File

actual object PluginManager {

    @Throws(Throwable::class)
    actual fun init(pluginId: String): PluginInitResult? {
        if (pluginId.isEmpty()) return null
        var throwable: Throwable? = null

        try {
            val result = initNative(pluginId)
            if (result != null) return result
        } catch (t: Throwable) {
            throwable = t
            Logs.w(t)
        }

        throw throwable ?: PluginNotFoundException(pluginId)
    }

    private fun initNative(pluginId: String): PluginInitResult? {
        val info = Plugins.getPlugin(pluginId) ?: return null

        // internal so
        if (info.applicationInfo == null) {
            try {
                initNativeInternal(pluginId)?.let { return PluginInitResult(it) }
            } catch (t: Throwable) {
                Logs.w("initNativeInternal failed", t)
            }
            return null
        }

        try {
            initNativeFaster(info)?.let { return PluginInitResult(it) }
        } catch (t: Throwable) {
            Logs.w("initNativeFaster failed", t)
        }

        Logs.w("Init native returns empty result")
        return null
    }

    private fun initNativeInternal(pluginId: String): String? {
        fun soIfExist(soName: String): String? {
            val f = File(androidRepo.context.applicationInfo.nativeLibraryDir, soName)
            if (f.canExecute()) {
                return f.absolutePath
            }
            return null
        }
        return when (pluginId) {
            "hysteria-plugin" -> soIfExist("libhysteria.so")
            "hysteria2-plugin" -> soIfExist("libhysteria2.so")
            "juicity-plugin" -> soIfExist("libjuicity.so")
            "naive-plugin" -> soIfExist("libnaive.so")
            "mieru-plugin" -> soIfExist("libmieru.so")
            "shadowquic-plugin" -> soIfExist("libshadowquic.so")
            else -> null
        }
    }

    private fun initNativeFaster(provider: ProviderInfo): String? {
        return provider.loadString(Plugins.METADATA_KEY_EXECUTABLE_PATH)
            ?.let { relativePath ->
                File(provider.applicationInfo.nativeLibraryDir).resolve(relativePath).apply {
                    check(canExecute())
                }.absolutePath
            }
    }
}
