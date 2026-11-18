package fr.husi.libcore

import fr.husi.BuildConfig
import fr.husi.CertProvider
import fr.husi.bg.AndroidPlatformInterface
import fr.husi.repository.resolveAndroidRepository
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.resolve

fun createBoxService(isBgProcess: Boolean): Service? {
    return if (isBgProcess) {
        val service = Libcore.newService(BuildConfig.VERSION_NAME, AndroidPlatformInterface())
        // Same parent directory StartService uses for the long-lived pool; URL
        // tests create transient subdirs under it via pluginpool.RunWithPlugins.
        val pluginDir = resolveAndroidRepository().noBackupFilesDir.resolve("plugin")
        pluginDir.createDirectories()
        service.setPluginWorkingDir(pluginDir.absolutePath())
        service
    } else {
        null
    }
}

fun loadCA(provider: Int) {
    val certOption = when (provider) {
        CertProvider.SYSTEM -> Libcore.CertSystem
        CertProvider.MOZILLA -> Libcore.CertMozilla
        CertProvider.SYSTEM_AND_USER -> Libcore.CertWithUserTrust
        CertProvider.CHROME -> Libcore.CertChrome
        else -> Libcore.CertSystem
    }
    Libcore.setupRootCA(certOption)
}
