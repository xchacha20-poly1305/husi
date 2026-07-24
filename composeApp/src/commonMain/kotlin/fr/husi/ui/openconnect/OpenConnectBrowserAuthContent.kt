package fr.husi.ui.openconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.auth_browser_callback_hint
import fr.husi.resources.auth_cookie
import fr.husi.resources.auth_final_url
import fr.husi.resources.auth_response_header
import org.jetbrains.compose.resources.stringResource

@Composable
fun OpenConnectBrowserAuthContent(
    request: OpenConnectBrowserRequestState,
    finalUrl: String,
    onFinalUrlChange: (String) -> Unit,
    cookies: SnapshotStateMap<String, String>,
    headers: SnapshotStateMap<String, String>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(Res.string.auth_browser_callback_hint))
        if (request.callbackUrlPrefixes.isNotEmpty()) {
            Text(request.callbackUrlPrefixes.joinToString("\n"))
        }
        if (
            request.completionMode == OpenConnectBrowserCompletionMode.Callback ||
            request.completionMode == OpenConnectBrowserCompletionMode.Cookie
        ) {
            OutlinedTextField(
                value = finalUrl,
                onValueChange = onFinalUrlChange,
                label = { Text(stringResource(Res.string.auth_final_url)) },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        for (name in request.cookieNames) {
            OutlinedTextField(
                value = cookies[name].orEmpty(),
                onValueChange = { cookies[name] = it },
                label = { Text("${stringResource(Res.string.auth_cookie)}: $name") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        for (name in request.earlyCookieNames) {
            if (name in request.cookieNames) continue
            OutlinedTextField(
                value = cookies[name].orEmpty(),
                onValueChange = { cookies[name] = it },
                label = { Text("${stringResource(Res.string.auth_cookie)}: $name") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        for (name in request.headerNames) {
            OutlinedTextField(
                value = headers[name].orEmpty(),
                onValueChange = { headers[name] = it },
                label = { Text("${stringResource(Res.string.auth_response_header)}: $name") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
