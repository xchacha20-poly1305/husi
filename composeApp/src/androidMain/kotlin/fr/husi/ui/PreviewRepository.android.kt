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
import fr.husi.repository.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File

private class PreviewAndroidRepository(
    override val context: Context,
    private val root: File = createPreviewRoot(),
) : AndroidRepository {
    override val isMainProcess = true
    override val isBgProcess = false
    override val isTv = false

    override val boxService: Service? = null

    override val preferenceStoreDispatcher = Dispatchers.Unconfined

    override val cacheDir: File = root.resolve("cache").apply { mkdirs() }
    override val filesDir: File = root.resolve("files").apply { mkdirs() }
    override val externalAssetsDir: File = root.resolve("external").apply { mkdirs() }
    override val noBackupFilesDir: File = root.resolve("no_backup").apply { mkdirs() }

    override fun resolveDatabaseFile(name: String): File = root.resolve(name)

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
internal actual fun previewRepository(): Repository {
    val context = LocalContext.current
    return remember(context) { PreviewAndroidRepository(context) }
}
