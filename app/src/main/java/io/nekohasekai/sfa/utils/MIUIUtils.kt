package io.nekohasekai.sfa.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Process

object MIUIUtils {

    val isMIUI by lazy {
        !getSystemProperty("ro.miui.ui.version.name").isNullOrBlank()
    }

    @SuppressLint("PrivateApi")
    fun getSystemProperty(key: String?): String? {
        try {
            return Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (ignored: Exception) {
        }
        return null
    }

    fun openPermissionSettings(context: Context) {
        val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
        intent.putExtra("extra_package_uid", Process.myUid())
        intent.putExtra("extra_pkgname", context.packageName)
        context.startActivity(intent)
    }

}