package fr.husi.ktx

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Process

object MIUIUtils {

    private const val MIUI_VERSION_CODE = "ro.miui.ui.version.code"
    private const val MIUI_PERMISSION_EDITOR = "miui.intent.action.APP_PERM_EDITOR"
    private const val EXTRA_PACKAGE_UID = "extra_package_uid"
    private const val EXTRA_PACKAGE_NAME = "extra_pkgname"

    val isMIUI by lazy {
        !getSystemProperty(MIUI_VERSION_CODE).isNullOrBlank()
    }

    @SuppressLint("PrivateApi")
    fun getSystemProperty(key: String?): String? {
        return try {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (_: Exception) {
            null
        }
    }

    fun openPermissionSettings(context: Context) {
        val intent = Intent(MIUI_PERMISSION_EDITOR)
        intent.putExtra(EXTRA_PACKAGE_NAME, Process.myUid())
        intent.putExtra(EXTRA_PACKAGE_UID, context.packageName)
        context.startActivity(intent)
    }

}