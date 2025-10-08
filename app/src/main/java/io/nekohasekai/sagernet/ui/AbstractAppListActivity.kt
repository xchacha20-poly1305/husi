package io.nekohasekai.sagernet.ui

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable

internal data class ProxiedApp(
    private val appInfo: ApplicationInfo,
    val packageName: String,
    var isProxied: Boolean,
    val icon: Drawable,
    val name: String, // cached for sorting
) {
    val uid get() = appInfo.uid
}
