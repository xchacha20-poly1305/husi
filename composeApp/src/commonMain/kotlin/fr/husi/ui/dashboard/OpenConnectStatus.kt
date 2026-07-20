package fr.husi.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Text
import fr.husi.ktx.formatLocalDateTime
import fr.husi.resources.Res
import fr.husi.resources.action_openconnect
import fr.husi.resources.auth_open_url
import fr.husi.resources.auth_required
import fr.husi.resources.auth_submit
import fr.husi.resources.cancel_auth
import fr.husi.resources.connected
import fr.husi.resources.connected_since
import fr.husi.resources.connecting
import fr.husi.resources.dns
import fr.husi.resources.error_title
import fr.husi.resources.ipv4
import fr.husi.resources.ipv6
import fr.husi.resources.mtu
import fr.husi.resources.server_address
import fr.husi.resources.state
import fr.husi.resources.transport
import fr.husi.ui.openconnect.OPENCONNECT_STATE_AUTH_PENDING
import fr.husi.ui.openconnect.OPENCONNECT_STATE_CONNECTED
import fr.husi.ui.openconnect.OPENCONNECT_STATE_ERROR
import fr.husi.ui.openconnect.OpenConnectAuthController
import fr.husi.ui.openconnect.OpenConnectAuthChallengeContent
import fr.husi.ui.openconnect.OpenConnectAuthChallengeState
import fr.husi.ui.openconnect.OpenConnectBrowserResultState
import fr.husi.ui.openconnect.OpenConnectEndpointState
import fr.husi.ui.openconnect.OpenConnectTunnelInfoState
import fr.husi.ui.openconnect.PlatformOpenConnectBrowserDialog
import fr.husi.ui.openconnect.initialAuthFormValues
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun OpenConnectStatusSection(
    controller: OpenConnectAuthController,
    showError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val endpoints by controller.endpoints.collectAsStateWithLifecycle()
    if (endpoints.isEmpty()) return

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.action_openconnect),
                style = MaterialTheme.typography.titleMedium,
            )
            for ((index, endpoint) in endpoints.withIndex()) {
                if (index > 0) HorizontalDivider()
                EndpointContent(
                    endpoint = endpoint,
                    showTag = endpoints.size > 1,
                    controller = controller,
                    showError = showError,
                )
            }
        }
    }
}

@Composable
private fun EndpointContent(
    endpoint: OpenConnectEndpointState,
    showTag: Boolean,
    controller: OpenConnectAuthController,
    showError: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTag) {
            Text(endpoint.tag, style = MaterialTheme.typography.titleMedium)
        }
        InfoRow(
            label = stringResource(Res.string.state),
            value = stateText(endpoint.state),
            color = stateColor(endpoint.state),
        )
        if (endpoint.error.isNotEmpty()) {
            Text(
                endpoint.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        endpoint.tunnelInfo?.takeIf {
            endpoint.state == OPENCONNECT_STATE_CONNECTED
        }?.let { TunnelInfoContent(it) }
        endpoint.authChallenge?.takeIf {
            endpoint.state == OPENCONNECT_STATE_AUTH_PENDING
        }?.let { challenge ->
            AuthSection(
                endpointTag = endpoint.tag,
                challenge = challenge,
                controller = controller,
                showError = showError,
            )
        }
    }
}

@Composable
private fun AuthSection(
    endpointTag: String,
    challenge: OpenConnectAuthChallengeState,
    controller: OpenConnectAuthController,
    showError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val form = challenge.form
    val browser = challenge.browser
    val values = remember(endpointTag, challenge.id) {
        mutableStateMapOf<String, String>().also { values ->
            form?.let { values.putAll(initialAuthFormValues(it)) }
        }
    }
    var finalUrl by remember(endpointTag, challenge.id) {
        mutableStateOf(browser?.finalUrl.orEmpty())
    }
    val cookies = remember(endpointTag, challenge.id) {
        mutableStateMapOf<String, String>().also { values ->
            browser?.cookieNames?.forEach { values[it] = "" }
        }
    }
    val headers = remember(endpointTag, challenge.id) {
        mutableStateMapOf<String, String>().also { values ->
            browser?.headerNames?.forEach { values[it] = "" }
        }
    }
    var working by remember(challenge.id) { mutableStateOf(false) }
    var showBrowser by remember(endpointTag, challenge.id) { mutableStateOf(false) }

    if (browser != null) {
        OutlinedButton(onClick = { showBrowser = true }) {
            Text(stringResource(Res.string.auth_open_url))
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
                scope.launch {
                    working = true
                    controller.submitAuthChallenge(
                        endpointTag = endpointTag,
                        challenge = challenge,
                        formValues = form?.let { values.toMap() },
                        browserResult = result,
                    )?.let(showError)
                    working = false
                }
            },
            onError = { error ->
                showBrowser = false
                showError(error)
            },
        )
    }
    OpenConnectAuthChallengeContent(
        challenge = challenge,
        values = values,
        browserFinalUrl = finalUrl,
        onBrowserFinalUrlChange = { finalUrl = it },
        cookies = cookies,
        headers = headers,
        enabled = !working,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        OutlinedButton(
            enabled = !working,
            onClick = {
                scope.launch {
                    working = true
                    controller.cancelAuthChallenge(endpointTag, challenge.id)?.let(showError)
                    working = false
                }
            },
        ) {
            Text(stringResource(Res.string.cancel_auth))
        }
        Button(
            enabled = !working,
            onClick = {
                scope.launch {
                    working = true
                    controller.submitAuthChallenge(
                        endpointTag = endpointTag,
                        challenge = challenge,
                        formValues = form?.let { values.toMap() },
                        browserResult = browser?.let {
                            OpenConnectBrowserResultState(
                                finalUrl = finalUrl,
                                cookies = cookies.toMap(),
                                headers = headers.toMap(),
                            )
                        },
                    )?.let(showError)
                    working = false
                }
            }
        ) {
            Text(stringResource(Res.string.auth_submit))
        }
    }
}

@Composable
private fun TunnelInfoContent(info: OpenConnectTunnelInfoState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (info.server.isNotEmpty()) {
            InfoRow(stringResource(Res.string.server_address), info.server)
        }
        if (info.transport.isNotEmpty()) {
            InfoRow(stringResource(Res.string.transport), info.transport)
        }
        if (info.ipv4.isNotEmpty()) {
            InfoRow(stringResource(Res.string.ipv4), info.ipv4.joinToString(", "))
        }
        if (info.ipv6.isNotEmpty()) {
            InfoRow(stringResource(Res.string.ipv6), info.ipv6.joinToString(", "))
        }
        if (info.dns.isNotEmpty()) {
            InfoRow(stringResource(Res.string.dns), info.dns.joinToString(", "))
        }
        if (info.mtu > 0) {
            InfoRow(stringResource(Res.string.mtu), info.mtu.toString())
        }
        if (info.connectedSince > 0) {
            InfoRow(
                stringResource(Res.string.connected_since),
                formatLocalDateTime(info.connectedSince),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.padding(end = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = color,
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun stateText(state: String): String = stringResource(
    when (state) {
        OPENCONNECT_STATE_CONNECTED -> Res.string.connected
        OPENCONNECT_STATE_AUTH_PENDING -> Res.string.auth_required
        OPENCONNECT_STATE_ERROR -> Res.string.error_title
        else -> Res.string.connecting
    },
)

@Composable
private fun stateColor(state: String): Color = when (state) {
    OPENCONNECT_STATE_CONNECTED -> MaterialTheme.colorScheme.primary
    OPENCONNECT_STATE_AUTH_PENDING -> MaterialTheme.colorScheme.tertiary
    OPENCONNECT_STATE_ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
