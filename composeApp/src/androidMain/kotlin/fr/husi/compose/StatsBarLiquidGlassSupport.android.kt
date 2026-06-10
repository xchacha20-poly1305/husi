package fr.husi.compose

import android.os.Build

internal actual fun isStatsBarLiquidGlassRuntimeSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
