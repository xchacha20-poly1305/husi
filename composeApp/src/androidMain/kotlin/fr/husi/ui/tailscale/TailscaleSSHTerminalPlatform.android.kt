package fr.husi.ui.tailscale

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberOpenTailscaleSSHTerminal(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            context.startActivity(Intent(context, TailscaleSSHActivity::class.java))
        }
    }
}
