package fr.husi.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import fr.husi.ui.VpnRequestActivity

@Composable
actual fun rememberVpnServiceLauncher(onFailed: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(VpnRequestActivity.StartService()) { failed ->
        if (failed) onFailed()
    }
    return { launcher.launch(null) }
}
