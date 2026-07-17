package fr.husi.ui.openconnect

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextButton as Material3TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import fr.husi.compose.ScrollableDialog
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.auth_continue
import fr.husi.resources.auth_later
import fr.husi.resources.auth_open_url
import fr.husi.resources.auth_submit
import fr.husi.resources.auth_verifying
import fr.husi.resources.openconnect_authentication
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Global dialog for a pending OpenConnect auth form.
 *
 * Dismissing it (back press, outside touch or "Later") only hides the
 * dialog; the form stays pending in the core and can be picked up again
 * from the OpenConnect status page.
 */
@Composable
fun OpenConnectAuthDialog(
    pending: PendingOpenConnectAuth,
    controller: OpenConnectAuthController,
    showError: (String) -> Unit,
    onDismissed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val form = pending.form
    val values = remember(pending.endpointTag, form.id) {
        mutableStateMapOf<String, String>().also {
            it.putAll(initialAuthFormValues(form))
        }
    }
    var submitting by remember(form.id) { mutableStateOf(false) }
    var submitted by remember(form.id) { mutableStateOf(false) }

    fun dismiss() {
        controller.dismissDialog(pending.endpointTag, form.id)
        onDismissed()
    }

    ScrollableDialog(
        onDismissRequest = ::dismiss,
        confirmButton = {
            if (form.url.isNotEmpty()) {
                TextButton(stringResource(Res.string.auth_open_url)) {
                    uriHandler.openUri(form.url)
                }
            } else if (!submitted) {
                Material3TextButton(
                    enabled = !submitting,
                    onClick = {
                        scope.launch {
                            submitting = true
                            val error = controller.submitAuthForm(
                                endpointTag = pending.endpointTag,
                                form = form,
                                values = values.toMap(),
                            )
                            submitting = false
                            if (error != null) {
                                showError(error)
                            } else {
                                submitted = true
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (form.fields.isEmpty()) {
                                Res.string.auth_continue
                            } else {
                                Res.string.auth_submit
                            },
                        ),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.auth_later), onClick = ::dismiss)
        },
        title = { Text(stringResource(Res.string.openconnect_authentication)) },
        text = {
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
                OpenConnectAuthFormContent(
                    form = form,
                    values = values,
                    enabled = !submitting,
                )
            }
        },
    )
}
