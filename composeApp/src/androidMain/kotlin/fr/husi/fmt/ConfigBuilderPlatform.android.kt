package fr.husi.fmt

import android.os.Build
import fr.husi.repository.resolveAndroidRepository
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.resolve

internal actual suspend fun SingBoxOptions.Inbound_TunOptions.applyPlatformConfig() {
}

internal actual val localDNSSupportRaw: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

internal actual val anchorDeviceName: String
    get() = Build.MODEL

internal actual val protectPath: String
    get() = resolveAndroidRepository().noBackupFilesDir
        .resolve(PROTECT_PATH_NAME)
        .absolutePath()
