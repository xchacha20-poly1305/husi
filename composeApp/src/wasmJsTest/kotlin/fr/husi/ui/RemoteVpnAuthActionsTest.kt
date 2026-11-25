@file:OptIn(ExperimentalCoroutinesApi::class)

package fr.husi.ui

import fr.husi.Key
import fr.husi.database.preference.long
import fr.husi.proto.daemon.OpenConnectAuthChallenge
import fr.husi.proto.daemon.OpenConnectEndpointStatus
import fr.husi.proto.daemon.OpenConnectStatusUpdate
import fr.husi.proto.daemon.OpenVPNChallenge
import fr.husi.proto.daemon.OpenVPNEndpointStatus
import fr.husi.proto.daemon.OpenVPNStatusUpdate
import fr.husi.remote.RemoteState
import fr.husi.test.FakePreferencesDataStore
import fr.husi.test.FakeRemoteClient
import fr.husi.test.FakeRemoteServerStore
import fr.husi.ui.openconnect.RemoteOpenConnectActions
import fr.husi.ui.openvpn.RemoteOpenVPNActions
import fr.husi.vpn.OPENCONNECT_STATE_AUTH_PENDING
import fr.husi.vpn.OPENCONNECT_STATE_CONNECTED
import fr.husi.vpn.OPENVPN_CHALLENGE_CREDENTIALS
import fr.husi.vpn.OPENVPN_STATE_AUTH_PENDING
import fr.husi.vpn.OPENVPN_STATE_CONNECTED
import fr.husi.vpn.vpnAuthChallengeKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteVpnAuthActionsTest {

    @Test
    fun `openconnect actions track keys dismiss undismiss clear and supersede`() = runTest {
        withRemote { remote ->
            val actions = RemoteOpenConnectActions(remote)
            actions.update(
                OpenConnectStatusUpdate(
                    endpoints = listOf(
                        OpenConnectEndpointStatus(tag1(), OPENCONNECT_STATE_CONNECTED),
                        OpenConnectEndpointStatus(
                            tag2(),
                            OPENCONNECT_STATE_AUTH_PENDING,
                            authChallenge = OpenConnectAuthChallenge(id = "c1"),
                        ),
                    ),
                ),
            )
            assertEquals(setOf(vpnAuthChallengeKey(tag2(), "c1")), actions.pendingChallengeKeys.value)
            assertEquals("c1", actions.pendingDialogAuth.value?.challenge?.id)

            actions.dismissDialog(tag2(), "c1")
            assertNull(actions.pendingDialogAuth.value)
            assertEquals(setOf(vpnAuthChallengeKey(tag2(), "c1")), actions.pendingChallengeKeys.value)

            actions.undismissDialog(tag2(), "c1")
            assertEquals("c1", actions.pendingDialogAuth.value?.challenge?.id)

            // Dismiss old challenge; new challenge supersedes and shows dialog.
            actions.dismissDialog(tag2(), "c1")
            assertNull(actions.pendingDialogAuth.value)
            actions.update(
                OpenConnectStatusUpdate(
                    endpoints = listOf(
                        OpenConnectEndpointStatus(
                            tag2(),
                            OPENCONNECT_STATE_AUTH_PENDING,
                            authChallenge = OpenConnectAuthChallenge(id = "c2"),
                        ),
                    ),
                ),
            )
            assertEquals("c2", actions.pendingDialogAuth.value?.challenge?.id)

            actions.dismissDialog(tag2(), "c2")
            assertNull(actions.pendingDialogAuth.value)
            actions.clearDismissed()
            assertEquals("c2", actions.pendingDialogAuth.value?.challenge?.id)
        }
    }

    @Test
    fun `openvpn actions track keys dismiss undismiss clear and supersede`() = runTest {
        withRemote { remote ->
            val actions = RemoteOpenVPNActions(remote)
            actions.update(
                OpenVPNStatusUpdate(
                    endpoints = listOf(
                        OpenVPNEndpointStatus(tag1(), OPENVPN_STATE_CONNECTED),
                        OpenVPNEndpointStatus(
                            tag2(),
                            OPENVPN_STATE_AUTH_PENDING,
                            challenge = OpenVPNChallenge(
                                id = "v1",
                                kind = OPENVPN_CHALLENGE_CREDENTIALS,
                            ),
                        ),
                    ),
                ),
            )
            assertEquals(setOf(vpnAuthChallengeKey(tag2(), "v1")), actions.pendingChallengeKeys.value)
            assertEquals("v1", actions.pendingDialogAuth.value?.challenge?.id)

            actions.dismissDialog(tag2(), "v1")
            assertNull(actions.pendingDialogAuth.value)

            actions.undismissDialog(tag2(), "v1")
            assertEquals("v1", actions.pendingDialogAuth.value?.challenge?.id)

            actions.dismissDialog(tag2(), "v1")
            actions.update(
                OpenVPNStatusUpdate(
                    endpoints = listOf(
                        OpenVPNEndpointStatus(
                            tag2(),
                            OPENVPN_STATE_AUTH_PENDING,
                            challenge = OpenVPNChallenge(
                                id = "v2",
                                kind = OPENVPN_CHALLENGE_CREDENTIALS,
                            ),
                        ),
                    ),
                ),
            )
            assertEquals("v2", actions.pendingDialogAuth.value?.challenge?.id)

            actions.dismissDialog(tag2(), "v2")
            actions.clearDismissed()
            assertEquals("v2", actions.pendingDialogAuth.value?.challenge?.id)
        }
    }

    private fun tag1() = "other"
    private fun tag2() = "office"

    private suspend fun TestScope.withRemote(block: suspend (RemoteState) -> Unit) {
        val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val remote = RemoteState(
            scope = scope,
            client = FakeRemoteClient(),
            serverStore = FakeRemoteServerStore(),
            speedInterval = flowOf(1000),
            activeServerId = FakePreferencesDataStore().preferences.long(Key.ACTIVE_REMOTE_SERVER_ID),
        )
        try {
            block(remote)
        } finally {
            remote.close()
        }
    }
}
