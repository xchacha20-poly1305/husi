package fr.husi.ui.openvpn

import fr.husi.vpn.OpenVPNChallengeState
import fr.husi.vpn.OpenVPNEndpointState
import kotlinx.coroutines.flow.StateFlow

interface OpenVPNAuthActions {
    val endpoints: StateFlow<List<OpenVPNEndpointState>>
    suspend fun submitAuthChallenge(endpointTag: String, challenge: OpenVPNChallengeState, username: String, password: String, secret: String): String?
    suspend fun cancelAuthChallenge(endpointTag: String, challengeId: String): String?
}
