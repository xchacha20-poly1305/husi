package fr.husi.ui.openvpn

import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.proto.daemon.openVPNChallenge
import fr.husi.proto.daemon.openVPNEndpointStatus
import fr.husi.proto.daemon.openVPNStatusUpdate
import fr.husi.test.FakeCoreClient
import fr.husi.test.MainDispatcherTest
import fr.husi.vpn.OPENVPN_CHALLENGE_CREDENTIALS
import fr.husi.vpn.OPENVPN_STATE_AUTH_PENDING
import fr.husi.vpn.OPENVPN_STATE_CONNECTED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class OpenVPNAuthControllerTest : MainDispatcherTest() {

    private val coreClient = FakeCoreClient()

    @AfterTest
    fun resetBackendState() {
        BackendState.reset()
    }

    @Test
    fun `pending dialog follows auth-pending challenge`() = runTest(dispatcher.scheduler) {
        val controller = newController()
        BackendState.updateState(ServiceState.Connected)
        advanceUntilIdle()

        coreClient.openVPNFlow.emit(statusUpdate(state = OPENVPN_STATE_AUTH_PENDING))
        advanceUntilIdle()

        val pending = assertNotNull(controller.pendingDialogAuth.value)
        assertEquals("home", pending.endpointTag)
        assertEquals("c1", pending.challenge.id)
        assertEquals(1, controller.endpoints.value.size)
    }

    @Test
    fun `dismissDialog hides pending challenge until a new one arrives`() =
        runTest(dispatcher.scheduler) {
            val controller = newController()
            BackendState.updateState(ServiceState.Connected)
            advanceUntilIdle()
            coreClient.openVPNFlow.emit(statusUpdate(challengeId = "c1"))
            advanceUntilIdle()

            controller.dismissDialog("home", "c1")
            advanceUntilIdle()
            assertNull(controller.pendingDialogAuth.value)

            coreClient.openVPNFlow.emit(statusUpdate(challengeId = "c2"))
            advanceUntilIdle()
            assertEquals("c2", controller.pendingDialogAuth.value?.challenge?.id)
        }

    @Test
    fun `submitAuthChallenge sends username password and secret`() = runTest(dispatcher.scheduler) {
        val controller = newController()
        BackendState.updateState(ServiceState.Connected)
        advanceUntilIdle()
        coreClient.openVPNFlow.emit(statusUpdate())
        advanceUntilIdle()
        val challenge = assertNotNull(controller.pendingDialogAuth.value).challenge

        val error = controller.submitAuthChallenge(
            endpointTag = "home",
            challenge = challenge,
            username = "alice",
            password = "secret",
            secret = "123456",
        )
        advanceUntilIdle()

        assertNull(error)
        val submission = assertNotNull(coreClient.lastOpenVPNSubmission)
        assertEquals("home", submission.endpointTag)
        assertEquals("c1", submission.challengeID)
        assertEquals("alice", submission.username)
        assertEquals("secret", submission.password)
        assertEquals("123456", submission.secret)
    }

    @Test
    fun `cancelAuthChallenge reports the core error`() = runTest(dispatcher.scheduler) {
        val controller = newController()
        coreClient.cancelOpenVPNThrowable = IllegalStateException("stopped")

        val error = controller.cancelAuthChallenge("home", "c1")
        advanceUntilIdle()

        assertEquals("stopped", error)
        assertEquals("home" to "c1", coreClient.lastOpenVPNCancel)
    }

    @Test
    fun `stopping the service clears endpoints`() = runTest(dispatcher.scheduler) {
        val controller = newController()
        BackendState.updateState(ServiceState.Connected)
        advanceUntilIdle()
        coreClient.openVPNFlow.emit(statusUpdate(state = OPENVPN_STATE_CONNECTED, challengeId = null))
        advanceUntilIdle()
        assertEquals(1, controller.endpoints.value.size)

        BackendState.updateState(ServiceState.Stopped)
        advanceUntilIdle()
        assertEquals(emptyList(), controller.endpoints.value)
        assertNull(controller.pendingDialogAuth.value)
    }

    private fun newController() = OpenVPNAuthController(
        coreClient = coreClient,
        ioDispatcher = dispatcher,
    )

    private fun statusUpdate(
        state: String = OPENVPN_STATE_AUTH_PENDING,
        challengeId: String? = "c1",
    ) = openVPNStatusUpdate {
        endpoints += openVPNEndpointStatus {
            endpointTag = "home"
            this.state = state
            if (challengeId != null) {
                challenge = openVPNChallenge {
                    id = challengeId
                    kind = OPENVPN_CHALLENGE_CREDENTIALS
                    username = "alice"
                }
            }
        }
    }
}
