package io.nekohasekai.sagernet.ktx

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

inline fun <reified T : Activity> Context.findActivity(): T? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is T) return ctx
        ctx = ctx.baseContext
    }
    return null
}

fun Context.openPermissionSettings() {
    if (MIUIUtils.isMIUI) runCatching {
        MIUIUtils.openPermissionSettings(this)
    }.onSuccess {
        return
    }

    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.data = "package:$packageName".toUri()
    startActivity(intent)
}