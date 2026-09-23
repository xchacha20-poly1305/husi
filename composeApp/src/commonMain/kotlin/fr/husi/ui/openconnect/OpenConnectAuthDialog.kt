package fr.husi.ui.openconnect

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.compose.TextButton
import fr.husi.compose.VerticalScrollColumn
import fr.husi.compose.WindowedDialog
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.auth_later
import fr.husi.resources.auth_open_url
import fr.husi.resources.auth_submit
import fr.husi.resources.auth_verifying
import fr.husi.resources.openconnect_authentication
import fr.husi.ui.AuthDialogWindowSize
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.SnackbarEmitter
import fr.husi.ui.StringOrRes
import fr.husi.vpn.OpenConnectAuthChallengeState
import fr.husi.vpn.OpenConnectBrowserResultState
import fr.husi.vpn.buildResult
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.TextButton as Material3TextButton

/**
 * Global dialog for a pending OpenConnect auth challenge.
 *
 * Dismissing it (back press, outside touch or "Later") only hides the
 * dialog; the challenge stays pending in the core and can be picked up again
 * from the OpenConnect status page.
 */
@Composable
fun OpenConnectAuthDialog(
    pending: PendingOpenConnectAuth,
    controller: OpenConnectAuthController,
    onDismissed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val challenge = pending.challenge
    val form = challenge.form
    val browser = challenge.browser
    val values = remember(pending.endpointTag, challenge.id) {
        mutableStateMapOf<String, String>().also { values ->
            form?.let { values.putAll(initialAuthFormValues(it)) }
        }
    }
    var finalUrl by remember(pending.endpointTag, challenge.id) {
        mutableStateOf(browser?.finalUrl.orEmpty())
    }
    val cookies = remember(pending.endpointTag, challenge.id) {
        mutableStateMapOf<String, String>().also { values ->
            (browser?.cookieNames.orEmpty() + browser?.earlyCookieNames.orEmpty())
                .distinct()
                .forEach { values[it] = "" }
        }
    }
    val headers = remember(pending.endpointTag, challenge.id) {
        mutableStateMapOf<String, String>().also { values ->
            browser?.headerNames?.forEach { values[it] = "" }
        }
    }
    var submitting by remember(challenge.id) { mutableStateOf(false) }
    var submitted by remember(challenge.id) { mutableStateOf(false) }
    var showBrowser by remember(pending.endpointTag, challenge.id) { mutableStateOf(false) }

    fun dismiss() {
        controller.dismissDialog(pending.endpointTag, challenge.id)
        onDismissed()
    }

    fun submit(
        snackbar: SnackbarEmitter,
        browserResult: OpenConnectBrowserResultState? = browser?.buildResult(
            finalUrl,
            cookies.toMap(),
            headers.toMap(),
        ),
    ) {
        scope.launch {
            submitting = true
            val error = controller.submitAuthChallenge(
                endpointTag = pending.endpointTag,
                challenge = challenge,
                formValues = form?.let { values.toMap() },
                browserResult = browserResult,
            )
            submitting = false
            if (error != null) {
                snackbar.show(StringOrRes.Direct(error))
            } else {
                submitted = true
            }
        }
    }

    WindowedDialog(
        onDismissRequest = ::dismiss,
        title = stringResource(Res.string.openconnect_authentication),
        windowSize = AuthDialogWindowSize,
        buttons = {
            val snackbar = LocalSnackbarEmitter.current
            TextButton(stringResource(Res.string.auth_later), onClick = ::dismiss)
            if (!submitted) {
                if (browser != null) {
                    TextButton(stringResource(Res.string.auth_open_url)) {
                        showBrowser = true
                    }
                }
                Material3TextButton(
                    enabled = !submitting,
                    onClick = { submit(snackbar) },
                ) {
                    Text(stringResource(Res.string.auth_submit))
                }
            }
        },
        content = {
            val snackbar = LocalSnackbarEmitter.current
            VerticalScrollColumn(contentPadding = PaddingValues(horizontal = 24.dp)) {
                if (submitted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            stringResource(Res.string.auth_verifying),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                } else {
                    OpenConnectAuthChallengeContent(
                        challenge = challenge,
                        values = values,
                        browserFinalUrl = finalUrl,
                        onBrowserFinalUrlChange = { finalUrl = it },
                        cookies = cookies,
                        headers = headers,
                        enabled = !submitting,
                    )
                }
            }
            browser?.let { request ->
                PlatformOpenConnectBrowserDialog(
                    challengeId = challenge.id,
                    request = request,
                    visible = showBrowser,
                    onDismiss = { showBrowser = false },
                    onResult = { result ->
                        showBrowser = false
                        submit(snackbar, browserResult = result)
                    },
                    onError = { error ->
                        showBrowser = false
                        snackbar.show(StringOrRes.Direct(error))
                    },
                )
            }
        },
    )
}

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
