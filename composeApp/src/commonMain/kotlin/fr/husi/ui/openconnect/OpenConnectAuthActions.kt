package fr.husi.ui.openconnect

import fr.husi.vpn.OpenConnectAuthChallengeState
import fr.husi.vpn.OpenConnectBrowserResultState
import fr.husi.vpn.OpenConnectEndpointState
import kotlinx.coroutines.flow.StateFlow

interface OpenConnectAuthActions {
    val endpoints: StateFlow<List<OpenConnectEndpointState>>
    suspend fun submitAuthChallenge(endpointTag: String, challenge: OpenConnectAuthChallengeState, formValues: Map<String, String>?, browserResult: OpenConnectBrowserResultState?): String?
    suspend fun cancelAuthChallenge(endpointTag: String, challengeId: String): String?
}
