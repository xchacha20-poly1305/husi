package fr.husi.ui.openconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import fr.husi.compose.material3.Text
import fr.husi.vpn.OpenConnectAuthChallengeState

@Composable
internal fun OpenConnectAuthChallengeContent(
    challenge: OpenConnectAuthChallengeState,
    values: SnapshotStateMap<String, String>,
    browserFinalUrl: String,
    onBrowserFinalUrlChange: (String) -> Unit,
    cookies: SnapshotStateMap<String, String>,
    headers: SnapshotStateMap<String, String>,
    enabled: Boolean,
) {
    if (challenge.banner.isNotEmpty()) {
        Text(challenge.banner)
    }
    if (challenge.message.isNotEmpty()) {
        Text(challenge.message)
    }
    if (challenge.error.isNotEmpty()) {
        Text(challenge.error)
    }
    challenge.form?.let { form ->
        OpenConnectAuthFormContent(form, values, enabled)
    }
    challenge.browser?.let { browser ->
        PlatformOpenConnectBrowserAuthContent(
            request = browser,
            finalUrl = browserFinalUrl,
            onFinalUrlChange = onBrowserFinalUrlChange,
            cookies = cookies,
            headers = headers,
            enabled = enabled,
        )
    }
}
