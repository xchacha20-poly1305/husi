package fr.husi.ui.openvpn

import fr.husi.proto.daemon.OpenVPNChallengeCancel
import fr.husi.proto.daemon.OpenVPNChallengeSubmission
import fr.husi.proto.daemon.OpenVPNStatusUpdate
import fr.husi.remote.RemoteState
import fr.husi.vpn.OPENVPN_STATE_AUTH_PENDING
import fr.husi.vpn.OpenVPNChallengeState
import fr.husi.vpn.OpenVPNEndpointState
import fr.husi.vpn.OpenVPNTunnelInfoState
import fr.husi.vpn.PendingVpnAuth
import fr.husi.vpn.vpnAuthChallengeKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class RemoteOpenVPNActions(private val state: RemoteState) : OpenVPNAuthActions {
    override val endpoints: StateFlow<List<OpenVPNEndpointState>>
        field = MutableStateFlow(emptyList())

    val pendingChallengeKeys: StateFlow<Set<String>>
        field = MutableStateFlow(emptySet())

    val pendingDialogAuth: StateFlow<PendingOpenVPNAuth?>
        field = MutableStateFlow(null)

    private val dismissedChallenges = mutableSetOf<String>()

    fun update(value: OpenVPNStatusUpdate): List<OpenVPNEndpointState> {
        val mapped = value.endpoints.map { endpoint ->
            OpenVPNEndpointState(
                tag = endpoint.endpointTag,
                state = endpoint.state,
                stateText = endpoint.stateText,
                error = endpoint.error,
                challenge = endpoint.challenge?.let { challenge ->
                    OpenVPNChallengeState(
                        id = challenge.id,
                        kind = challenge.kind,
                        username = challenge.username,
                        message = challenge.message,
                        url = challenge.url,
                        secretMessage = challenge.secretMessage,
                        echo = challenge.echo,
                        previousError = challenge.previousError,
                        deadline = challenge.deadline,
                    )
                },
                tunnelInfo = endpoint.tunnelInfo?.let { tunnel ->
                    OpenVPNTunnelInfoState(
                        server = tunnel.server,
                        network = tunnel.network,
                        cipher = tunnel.cipher,
                        ipv4 = tunnel.ipv4,
                        ipv6 = tunnel.ipv6,
                        dns = tunnel.dns,
                        mtu = tunnel.mtu,
                        connectedSince = tunnel.connectedSince,
                    )
                },
            )
        }
        endpoints.value = mapped
        recomputePending(mapped)
        return mapped
    }

    fun dismissDialog(endpointTag: String, challengeId: String) {
        dismissedChallenges.add(vpnAuthChallengeKey(endpointTag, challengeId))
        recomputePending(endpoints.value)
    }

    fun undismissDialog(endpointTag: String, challengeId: String) {
        if (dismissedChallenges.remove(vpnAuthChallengeKey(endpointTag, challengeId))) {
            recomputePending(endpoints.value)
        }
    }

    fun clearDismissed() {
        if (dismissedChallenges.isEmpty()) return
        dismissedChallenges.clear()
        recomputePending(endpoints.value)
    }

    private fun recomputePending(list: List<OpenVPNEndpointState>) {
        val keys = HashSet<String>()
        var firstDialog: PendingOpenVPNAuth? = null
        for (endpoint in list) {
            val challenge = endpoint.challenge?.takeIf {
                endpoint.state == OPENVPN_STATE_AUTH_PENDING
            } ?: continue
            val key = vpnAuthChallengeKey(endpoint.tag, challenge.id)
            keys.add(key)
            if (firstDialog == null && key !in dismissedChallenges) {
                firstDialog = PendingVpnAuth(endpoint.tag, challenge)
            }
        }
        pendingChallengeKeys.value = keys
        pendingDialogAuth.value = firstDialog
    }

    override suspend fun submitAuthChallenge(
        endpointTag: String,
        challenge: OpenVPNChallengeState,
        username: String,
        password: String,
        secret: String,
    ) = state.perform {
        SubmitOpenVPNChallengeResponse().execute(
            OpenVPNChallengeSubmission(endpointTag, challenge.id, username, password, secret),
        )
    }

    override suspend fun cancelAuthChallenge(endpointTag: String, challengeId: String) = state.perform {
        CancelOpenVPNChallenge().execute(OpenVPNChallengeCancel(endpointTag, challengeId))
    }
}
