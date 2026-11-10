package fr.husi.ui.openvpn

import fr.husi.proto.daemon.OpenVPNChallengeCancel
import fr.husi.proto.daemon.OpenVPNChallengeSubmission
import fr.husi.proto.daemon.OpenVPNStatusUpdate
import fr.husi.remote.RemoteState
import fr.husi.vpn.OpenVPNChallengeState
import fr.husi.vpn.OpenVPNEndpointState
import fr.husi.vpn.OpenVPNTunnelInfoState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class RemoteOpenVPNActions(private val state: RemoteState) : OpenVPNAuthActions {
    override val endpoints: StateFlow<List<OpenVPNEndpointState>>
        field = MutableStateFlow(emptyList())

    fun update(value: OpenVPNStatusUpdate) {
        endpoints.value =
            value.endpoints.map { endpoint ->
                OpenVPNEndpointState(
                    tag = endpoint.endpointTag,
                    state = endpoint.state,
                    stateText = endpoint.stateText,
                    error = endpoint.error,
                    challenge =
                        endpoint
                            .challenge
                            ?.let { challenge ->
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
                    tunnelInfo =
                        endpoint
                            .tunnelInfo
                            ?.let { tunnel ->
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
    }

    override suspend fun submitAuthChallenge(
        endpointTag: String,
        challenge: OpenVPNChallengeState,
        username: String,
        password: String,
        secret: String,
    ) = state.perform {
        submitOpenVPNChallengeResponse(
            OpenVPNChallengeSubmission(endpointTag, challenge.id, username, password, secret),
        )
    }

    override suspend fun cancelAuthChallenge(endpointTag: String, challengeId: String) = state.perform {
        cancelOpenVPNChallenge(OpenVPNChallengeCancel(endpointTag, challengeId))
    }
}
