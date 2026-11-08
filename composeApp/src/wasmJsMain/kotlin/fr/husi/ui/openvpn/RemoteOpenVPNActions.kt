package fr.husi.ui.openvpn

import fr.husi.remote.RemoteState
import fr.husi.remote.flag
import fr.husi.remote.items
import fr.husi.remote.number
import fr.husi.remote.obj
import fr.husi.remote.request
import fr.husi.remote.strings
import fr.husi.remote.text
import fr.husi.vpn.OpenVPNChallengeState
import fr.husi.vpn.OpenVPNEndpointState
import fr.husi.vpn.OpenVPNTunnelInfoState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

internal class RemoteOpenVPNActions(private val state: RemoteState) : OpenVPNAuthActions {
    override val endpoints: StateFlow<List<OpenVPNEndpointState>>
        field = MutableStateFlow(emptyList())

    fun update(value: JsonObject) {
        endpoints.value =
            value.items("endpoints").map { endpoint ->
                OpenVPNEndpointState(
                    tag = endpoint.text("endpointTag"),
                    state = endpoint.text("state"),
                    stateText = endpoint.text("stateText"),
                    error = endpoint.text("error"),
                    challenge =
                        endpoint
                            .obj("challenge")
                            .takeIf { it.isNotEmpty() }
                            ?.let { challenge ->
                                OpenVPNChallengeState(
                                    id = challenge.text("id"),
                                    kind = challenge.text("kind"),
                                    username = challenge.text("username"),
                                    message = challenge.text("message"),
                                    url = challenge.text("url"),
                                    secretMessage = challenge.text("secretMessage"),
                                    echo = challenge.flag("echo"),
                                    previousError = challenge.text("previousError"),
                                    deadline = challenge.number("deadline"),
                                )
                            },
                    tunnelInfo =
                        endpoint
                            .obj("tunnelInfo")
                            .takeIf { it.isNotEmpty() }
                            ?.let { tunnel ->
                                OpenVPNTunnelInfoState(
                                    server = tunnel.text("server"),
                                    network = tunnel.text("network"),
                                    cipher = tunnel.text("cipher"),
                                    ipv4 = tunnel.strings("ipv4"),
                                    ipv6 = tunnel.strings("ipv6"),
                                    dns = tunnel.strings("dns"),
                                    mtu = tunnel.number("mtu").toInt(),
                                    connectedSince = tunnel.number("connectedSince"),
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
    ) =
        state.perform(
            "SubmitOpenVPNChallengeResponse",
            request(
                "endpointTag" to endpointTag,
                "challengeID" to challenge.id,
                "username" to username,
                "password" to password,
                "secret" to secret,
            ),
        )

    override suspend fun cancelAuthChallenge(endpointTag: String, challengeId: String) =
        state.perform(
            "CancelOpenVPNChallenge",
            request("endpointTag" to endpointTag, "challengeID" to challengeId),
        )
}
