package fr.husi.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.QRCodeDialog
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Text
import fr.husi.ktx.formatLocalDateTime
import fr.husi.ktx.readableMessage
import fr.husi.resources.Res
import fr.husi.resources.action_openvpn
import fr.husi.resources.auth_open_url
import fr.husi.resources.auth_submit
import fr.husi.resources.cancel_auth
import fr.husi.resources.cipher
import fr.husi.resources.connected_since
import fr.husi.resources.dns
import fr.husi.resources.ipv4
import fr.husi.resources.ipv6
import fr.husi.resources.mtu
import fr.husi.resources.network
import fr.husi.resources.server_address
import fr.husi.resources.share_qr_nfc
import fr.husi.resources.state
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import fr.husi.ui.openvpn.OpenVPNAuthChallengeContent
import fr.husi.ui.openvpn.OpenVPNAuthController
import fr.husi.vpn.OPENVPN_CHALLENGE_CREDENTIALS
import fr.husi.vpn.OPENVPN_CHALLENGE_OPEN_URL
import fr.husi.vpn.OPENVPN_STATE_AUTH_PENDING
import fr.husi.vpn.OPENVPN_STATE_CONNECTED
import fr.husi.vpn.OpenVPNChallengeState
import fr.husi.vpn.OpenVPNEndpointState
import fr.husi.vpn.OpenVPNTunnelInfoState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
internal fun OpenVPNStatusSection(
    controller: OpenVPNAuthController,
    showError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val endpoints by controller.endpoints.collectAsStateWithLifecycle()
    VpnEndpointStatusSection(
        title = stringResource(Res.string.action_openvpn),
        endpoints = endpoints,
        modifier = modifier,
    ) { endpoint, showTag ->
        EndpointContent(
            endpoint = endpoint,
            showTag = showTag,
            controller = controller,
            showError = showError,
        )
    }
}

@Composable
private fun EndpointContent(
    endpoint: OpenVPNEndpointState,
    showTag: Boolean,
    controller: OpenVPNAuthController,
    showError: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showTag) {
            Text(endpoint.tag, style = MaterialTheme.typography.titleMedium)
        }
        VpnStatusInfoRow(
            label = stringResource(Res.string.state),
            value = vpnAuthStateText(endpoint.state, endpoint.stateText),
            color = vpnAuthStateColor(endpoint.state),
        )
        if (endpoint.error.isNotEmpty()) {
            Text(
                endpoint.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        endpoint.tunnelInfo?.takeIf {
            endpoint.state == OPENVPN_STATE_CONNECTED
        }?.let { TunnelInfoContent(it) }
        endpoint.challenge?.takeIf {
            endpoint.state == OPENVPN_STATE_AUTH_PENDING
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
    challenge: OpenVPNChallengeState,
    controller: OpenVPNAuthController,
    showError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val snackbar = LocalSnackbarEmitter.current
    var username by remember(endpointTag, challenge.id) { mutableStateOf(challenge.username) }
    var password by remember(endpointTag, challenge.id) { mutableStateOf("") }
    var secret by remember(endpointTag, challenge.id) { mutableStateOf("") }
    var working by remember(challenge.id) { mutableStateOf(false) }
    var showQr by remember(endpointTag, challenge.id) { mutableStateOf(false) }
    var nowEpochSeconds by remember { mutableLongStateOf(Clock.System.now().epochSeconds) }
    val expired = challenge.deadline in 1..nowEpochSeconds
    val editable = !working && !expired

    if (challenge.deadline > 0) {
        LaunchedEffect(challenge.id) {
            while (true) {
                delay(1000)
                nowEpochSeconds = Clock.System.now().epochSeconds
            }
        }
    }

    if (challenge.kind == OPENVPN_CHALLENGE_OPEN_URL && challenge.url.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            OutlinedButton(
                onClick = {
                    runCatching { uriHandler.openUri(challenge.url) }
                        .onFailure { showError(it.readableMessage) }
                },
            ) {
                Text(stringResource(Res.string.auth_open_url))
            }
            OutlinedButton(onClick = { showQr = true }) {
                Text(stringResource(Res.string.share_qr_nfc))
            }
        }
    }
    if (showQr && challenge.url.isNotEmpty()) {
        QRCodeDialog(
            url = challenge.url,
            name = endpointTag,
            onDismiss = { showQr = false },
            showSnackbar = { snackbar.show(StringOrRes.Direct(it)) },
        )
    }
    OpenVPNAuthChallengeContent(
        challenge = challenge,
        username = username,
        onUsernameChange = { username = it },
        password = password,
        onPasswordChange = { password = it },
        secret = secret,
        onSecretChange = { secret = it },
        nowEpochSeconds = nowEpochSeconds,
        enabled = editable,
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
        if (challenge.answerable) {
            Button(
                enabled = editable,
                onClick = {
                    scope.launch {
                        working = true
                        controller.submitAuthChallenge(
                            endpointTag = endpointTag,
                            challenge = challenge,
                            username = if (challenge.kind == OPENVPN_CHALLENGE_CREDENTIALS) {
                                username
                            } else {
                                ""
                            },
                            password = if (challenge.kind == OPENVPN_CHALLENGE_CREDENTIALS) {
                                password
                            } else {
                                ""
                            },
                            secret = secret,
                        )?.let(showError)
                        working = false
                    }
                },
            ) {
                Text(stringResource(Res.string.auth_submit))
            }
        }
    }
}

@Composable
private fun TunnelInfoContent(info: OpenVPNTunnelInfoState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (info.server.isNotEmpty()) {
            VpnStatusInfoRow(stringResource(Res.string.server_address), info.server)
        }
        if (info.network.isNotEmpty()) {
            VpnStatusInfoRow(stringResource(Res.string.network), info.network)
        }
        if (info.cipher.isNotEmpty()) {
            VpnStatusInfoRow(stringResource(Res.string.cipher), info.cipher)
        }
        if (info.ipv4.isNotEmpty()) {
            VpnStatusInfoRow(stringResource(Res.string.ipv4), info.ipv4.joinToString(", "))
        }
        if (info.ipv6.isNotEmpty()) {
            VpnStatusInfoRow(stringResource(Res.string.ipv6), info.ipv6.joinToString(", "))
        }
        if (info.dns.isNotEmpty()) {
            VpnStatusInfoRow(stringResource(Res.string.dns), info.dns.joinToString(", "))
        }
        if (info.mtu > 0) {
            VpnStatusInfoRow(stringResource(Res.string.mtu), info.mtu.toString())
        }
        if (info.connectedSince > 0) {
            VpnStatusInfoRow(
                stringResource(Res.string.connected_since),
                formatLocalDateTime(info.connectedSince),
            )
        }
    }
}


