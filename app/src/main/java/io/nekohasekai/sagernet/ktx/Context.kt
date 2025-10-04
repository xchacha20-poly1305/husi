package io.nekohasekai.sagernet.ktx

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

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
