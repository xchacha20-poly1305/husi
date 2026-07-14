package fr.husi.fmt

import android.os.Build

internal actual fun SingBoxOptions.Inbound_TunOptions.applyPlatformConfig() {
}

internal actual val localDNSSupportRaw: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

internal actual val anchorDeviceName: String
    get() = Build.MODEL
