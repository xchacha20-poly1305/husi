package fr.husi.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.UserManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import fr.husi.libcore.Service
import fr.husi.repository.AndroidRepository
import fr.husi.repository.LocalRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

private class PreviewAndroidRepository(
    override val context: Context,
    private val root: PlatformFile = createPreviewRoot(),
) : AndroidRepository {
    override val isMainProcess = true
    override val isBgProcess = false
    override val isTv = false

    override val boxService: Service? = null

    override val preferenceStoreDispatcher = Dispatchers.Unconfined

    override val cacheDir: PlatformFile = root.resolve("cache").apply { createDirectories() }
    override val filesDir: PlatformFile = root.resolve("files").apply { createDirectories() }
    override val externalAssetsDir: PlatformFile = root.resolve("external").apply { createDirectories() }
    override val noBackupFilesDir: PlatformFile = root.resolve("no_backup").apply { createDirectories() }

    override fun resolveDatabaseFile(name: String): PlatformFile = root.resolve(name)

    override fun createConfigurationDataStore(scope: CoroutineScope): DataStore<Preferences> =
        previewConfigurationDataStore(root, scope)

    override val configureIntent: (Context) -> PendingIntent = {
        PendingIntent.getActivity(it, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
    }
    override val connectivity: ConnectivityManager by lazy { context.getSystemService()!! }
    override val user: UserManager by lazy { context.getSystemService()!! }
    override val power: PowerManager by lazy { context.getSystemService()!! }
    override val wifi: WifiManager by lazy { context.getSystemService()!! }
    override val packageManager: PackageManager by lazy { context.packageManager }

    override suspend fun updateNotificationChannels() = Unit

    override fun startService() = Unit
    override fun reloadService() = Unit
    override fun stopService() = Unit
}

@Composable
internal actual fun previewRepository(): LocalRepository {
    val context = LocalContext.current
    return remember(context) { PreviewAndroidRepository(context) }
}
