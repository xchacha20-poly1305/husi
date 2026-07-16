package fr.husi.ui

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fr.husi.fmt.PluginEntry
import fr.husi.ktx.Logs
import fr.husi.plugin.Plugins
import fr.husi.plugin.loadString
import fr.husi.utils.PackageCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal actual fun platformPluginsFlow(): Flow<List<PluginDisplay>> = flow {
    PackageCache.awaitLoadSync()
    val list = buildList {
        for ((packageName, plugin) in PackageCache.installedPluginPackages) try {
            val id = plugin.providers!![0].loadString(Plugins.METADATA_KEY_ID)
            if (id.isNullOrBlank()) continue
            add(
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
    emit(list)
}

@Composable
internal actual fun rememberOpenPluginCard(): (PluginDisplay) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { plugin ->
            context.startActivity(
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
    }
}

private fun PackageInfo.versionCodeCompat(): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }
}
