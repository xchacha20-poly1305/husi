package fr.husi.platform

import android.os.Build

actual object PlatformAbis {
    actual val supported: List<String> = Build.SUPPORTED_ABIS.orEmpty().toList()
}
