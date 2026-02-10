package fr.husi.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fr.husi.ktx.blankAsNull

@Composable
internal actual fun rememberOpenProcessAppInfo(process: String?): (() -> Unit)? {
    val context = LocalContext.current
    val packageName = process?.trim().blankAsNull() ?: return null
    return remember(context, packageName) {
        {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null)),
            )
        }
    }
}
