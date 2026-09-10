package fr.husi.plugin

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.Signature
import android.os.Build
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.sha256Hex
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.app_name
import fr.husi.utils.PackageCache
import kotlinx.coroutines.runBlocking

enum class PluginPublisher(
    val authoritiesPrefix: String,
    val trustedCertificates: Set<String>,
) {
    HUSI(
        "fr.husi.plugin.",
        setOf("55d8507c1b28d64236004ebb5da704e37ace3f102e9f90c6e4ec4182cd390e9e"),
    ) {
        override val displayName: String
            get() = runBlocking { resolveRepository().getString(Res.string.app_name) }
    },

    // https://github.com/ExclaveNetwork/Exclave
    EXCLAVE(
        "com.github.dyhkwong.",
        setOf("e9fe39e1ce254c50c2f9470a757b378c0b7cc536119867f7691405b592e6994b"),
    ) {
        override val displayName get() = "Exclave"
    },

    // https://github.com/SagerNet/SagerNet
    // https://github.com/klzgrad/naiveproxy
    // Due to SagerNet archived, naive publishes plugin with klzgrad's sign.
    SAGERNET(
        "io.nekohasekai.sagernet.plugin.",
        setOf(
            "32250a4b5f3a6733df57a3b9ec16c38d2c7fc5f2f693a9636f8f7b3be3549641", // nekohasekai
            "b7baa7fb895988eb731adb41882b53ade139f77de537b7c011ec33bd9677cbae", // klzgrad
        ),
    ) {
        override val displayName get() = "SagerNet"
    },

    // https://github.com/MatsuriDayo/plugins
    MATSURI(
        "moe.matsuri.exe.",
        setOf("35762758ce86a6ec297d9ccac689469bc43b9fed8ae1b27f100a86bbac00a055"),
    ) {
        override val displayName get() = "Matsuri"
    },
    ;

    abstract val displayName: String

    companion object {
        fun ofAuthority(authority: String): PluginPublisher? {
            return entries.find { authority.startsWith(it.authoritiesPrefix) }
        }
    }
}

object Plugins {
    // const val ACTION_NATIVE_PLUGIN = "io.nekohasekai.sagernet.plugin.ACTION_NATIVE_PLUGIN"

    const val METADATA_KEY_ID = "io.nekohasekai.sagernet.plugin.id"
    const val METADATA_KEY_EXECUTABLE_PATH = "io.nekohasekai.sagernet.plugin.executable_path"

    private val customAuthoritiesPrefixes: Set<String> = DataStore.customPluginPrefix
        .getBlocking()
        .split("\n")
        .filterTo(HashSet()) { it.isNotBlank() && it != "." }

    private val ownCertificates: Set<String> by lazy {
        PackageCache.packageManager.signingCertificates(PackageCache.context.packageName)
    }

    fun isPlugin(pkg: PackageInfo): Boolean {
        val authority = pkg.providers?.firstOrNull()?.authority ?: return false

        val publisher = PluginPublisher.ofAuthority(authority)
            ?: return customAuthoritiesPrefixes.any { authority.startsWith(it) }

        val trusted = when (publisher) {
            PluginPublisher.HUSI -> publisher.trustedCertificates + ownCertificates
            else -> publisher.trustedCertificates
        }
        val certificates = PackageCache.packageManager.signingCertificates(pkg.packageName)
        if (certificates.none { it in trusted }) {
            Logs.w(
                "plugin ${pkg.packageName} was not signed by ${publisher.displayName}: $certificates",
            )
            return false
        }
        return true
    }

    fun displayExeProvider(packageName: String): String {
        return PluginPublisher.ofAuthority(packageName)?.displayName ?: "Unknown"
    }

    fun getPlugin(pluginId: String): ProviderInfo? {
        if (pluginId.isBlank()) return null
        getPluginExternal(pluginId)?.let { return it }
        // internal so
        return ProviderInfo().apply { authority = PluginPublisher.HUSI.authoritiesPrefix }
    }

    fun getPluginExternal(pluginId: String): ProviderInfo? {
        if (pluginId.isBlank()) return null

        val providers = getExtPlugin(pluginId)
        for (builtin in PluginPublisher.entries) {
            providers.find { it.authority.startsWith(builtin.authoritiesPrefix) }?.let {
                return it
            }
        }

        return providers.randomOrNull()
    }

    private fun getExtPlugin(pluginId: String): List<ProviderInfo> {
        PackageCache.awaitLoadSync()
        val pkgs = PackageCache.installedPluginPackages
            .map { it.value }
            .filter { it.providers!![0].loadString(METADATA_KEY_ID) == pluginId }
        return pkgs.map { it.providers!![0] }
    }

}

private fun PackageManager.signingCertificates(packageName: String): Set<String> {
    val signatures: Array<Signature>? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ).signingInfo
            if (signingInfo?.hasMultipleSigners() == true) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo?.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        }
    } catch (e: PackageManager.NameNotFoundException) {
        Logs.w(e)
        return emptySet()
    }

    return signatures.orEmpty().mapTo(HashSet()) { it.toByteArray().sha256Hex() }
}
