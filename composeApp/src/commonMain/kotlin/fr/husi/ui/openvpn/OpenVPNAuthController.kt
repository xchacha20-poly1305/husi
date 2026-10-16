package fr.husi.ui.openvpn

import fr.husi.core.remote.CoreTarget
import fr.husi.proto.daemon.openVPNChallengeSubmission
import fr.husi.vpn.OPENVPN_STATE_AUTH_PENDING
import fr.husi.vpn.OpenVPNChallengeState
import fr.husi.vpn.OpenVPNEndpointState
import fr.husi.vpn.PendingVpnAuth
import fr.husi.vpn.VpnAuthSession
import fr.husi.vpn.toState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

typealias PendingOpenVPNAuth = PendingVpnAuth<OpenVPNChallengeState>

class OpenVPNAuthController(
    targets: StateFlow<CoreTarget?>,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val session = VpnAuthSession(
        targets = targets,
        subscribe = { client ->
            client.subscribeOpenVPNStatus().map { update ->
                update.endpointsList.map { it.toState() }
            }
        },
        pendingOf = { endpoint ->
            endpoint.challenge?.takeIf {
                endpoint.state == OPENVPN_STATE_AUTH_PENDING
            }?.let { PendingVpnAuth(endpoint.tag, it) }
        },
        challengeId = { it.id },
        logLabel = "openvpn",
        dispatcher = ioDispatcher,
    )

    val endpoints: StateFlow<List<OpenVPNEndpointState>>
        get() = session.endpoints

    val pendingDialogAuth: StateFlow<PendingOpenVPNAuth?>
        get() = session.pendingDialogAuth

    fun dismissDialog(endpointTag: String, challengeId: String) {
        session.dismissDialog(endpointTag, challengeId)
    }

    /** @return an error message, or null on success. */
    suspend fun submitAuthChallenge(
        endpointTag: String,
        challenge: OpenVPNChallengeState,
        username: String,
        password: String,
        secret: String,
    ): String? = session.perform("submit openvpn auth challenge") { target ->
        target.client.submitOpenVPNChallengeResponse(
            openVPNChallengeSubmission {
                this.endpointTag = endpointTag
                challengeID = challenge.id
                this.username = username
                this.password = password
                this.secret = secret
            },
        )
    }

    /** @return an error message, or null on success. */
    suspend fun cancelAuthChallenge(endpointTag: String, challengeId: String): String? =
        session.perform("cancel openvpn auth challenge") { target ->
            target.client.cancelOpenVPNChallenge(endpointTag, challengeId)
        }
}
