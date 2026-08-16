package fr.husi.ui.openvpn

import fr.husi.core.CoreClient
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
import org.koin.core.context.GlobalContext

typealias PendingOpenVPNAuth = PendingVpnAuth<OpenVPNChallengeState>

class OpenVPNAuthController(
    private val coreClient: CoreClient = GlobalContext.get().get(),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val session = VpnAuthSession(
        subscribe = {
            coreClient.subscribeOpenVPNStatus().map { update ->
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
    ): String? = session.perform("submit openvpn auth challenge") {
        coreClient.submitOpenVPNChallengeResponse(
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
        session.perform("cancel openvpn auth challenge") {
            coreClient.cancelOpenVPNChallenge(endpointTag, challengeId)
        }
}
