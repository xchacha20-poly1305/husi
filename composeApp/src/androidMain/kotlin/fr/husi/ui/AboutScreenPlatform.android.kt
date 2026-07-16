package fr.husi.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

@Composable
internal actual fun rememberShouldRequestBatteryOptimizations(): Boolean {
    val context = LocalContext.current
    val powerManger = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return remember(context, powerManger) {
        !powerManger.isIgnoringBatteryOptimizations(context.packageName)
    }
}

@SuppressLint("BatteryLife")
@Composable
internal actual fun rememberRequestIgnoreBatteryOptimizations(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            context.startActivity(
                Intent()
                    .setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData("package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
