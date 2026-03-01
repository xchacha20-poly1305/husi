package fr.husi.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import fr.husi.fmt.PluginEntry
import fr.husi.ktx.Logs
import fr.husi.plugin.Plugins
import fr.husi.plugin.loadString
import fr.husi.repository.androidRepo
import fr.husi.utils.PackageCache

internal actual suspend fun loadPlatformPlugins(onPlugin: suspend (PluginDisplay) -> Unit) {
    PackageCache.awaitLoadSync()
    for ((packageName, plugin) in PackageCache.installedPluginPackages) try {
        val id = plugin.providers!![0].loadString(Plugins.METADATA_KEY_ID)
        if (id.isNullOrBlank()) continue
        onPlugin(
            PluginDisplay(
                id = id,
                packageName = packageName,
                version = plugin.versionName ?: "unknown",
                versionCode = plugin.versionCodeCompat(),
                provider = Plugins.displayExeProvider(packageName),
                entry = PluginEntry.find(id),
            ),
        )
    } catch (e: Exception) {
        Logs.w(e)
    }
}

internal actual fun openPluginCard(plugin: PluginDisplay) {
    androidRepo.context.startActivity(
        Intent()
            .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(
                Uri.fromParts(
                    "package",
                    plugin.packageName,
                    null,
                ),
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun PackageInfo.versionCodeCompat(): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
}

@Composable
internal actual fun rememberShouldRequestBatteryOptimizations(): Boolean {
    val context = LocalContext.current
    val powerManger = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return remember(context, powerManger) {
        !powerManger.isIgnoringBatteryOptimizations(context.packageName)
    }
}

@SuppressLint("BatteryLife")
internal actual fun requestIgnoreBatteryOptimizations() {
    androidRepo.context.startActivity(
        Intent()
            .setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData("package:${androidRepo.context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
