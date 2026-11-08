package fr.husi.ui.openvpn

import fr.husi.core.remote.CoreTarget
import fr.husi.proto.daemon.openVPNChallenge
import fr.husi.proto.daemon.openVPNEndpointStatus
import fr.husi.proto.daemon.openVPNStatusUpdate
import fr.husi.test.FakeCoreClient
import fr.husi.test.MainDispatcherTest
import fr.husi.vpn.OPENVPN_CHALLENGE_CREDENTIALS
import fr.husi.vpn.OPENVPN_STATE_AUTH_PENDING
import fr.husi.vpn.OPENVPN_STATE_CONNECTED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class OpenVPNAuthControllerTest : MainDispatcherTest() {

    private val coreClient = FakeCoreClient()
    private val targets = MutableStateFlow<CoreTarget?>(null)

    @Test
    fun `pending dialog follows auth-pending challenge`() = runTest(dispatcher.scheduler) {
        val controller = newController()
        targets.value = CoreTarget(coreClient)
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
            targets.value = CoreTarget(coreClient)
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
        targets.value = CoreTarget(coreClient)
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
        targets.value = CoreTarget(coreClient)
        coreClient.cancelOpenVPNThrowable = IllegalStateException("stopped")

        val error = controller.cancelAuthChallenge("home", "c1")
        advanceUntilIdle()

        assertEquals("stopped", error)
        assertEquals("home" to "c1", coreClient.lastOpenVPNCancel)
    }

    @Test
    fun `stopping the target clears endpoints`() = runTest(dispatcher.scheduler) {
        val controller = newController()
        targets.value = CoreTarget(coreClient)
        advanceUntilIdle()
        coreClient.openVPNFlow.emit(statusUpdate(state = OPENVPN_STATE_CONNECTED, challengeId = null))
        advanceUntilIdle()
        assertEquals(1, controller.endpoints.value.size)

        targets.value = null
        advanceUntilIdle()
        assertEquals(emptyList(), controller.endpoints.value)
        assertNull(controller.pendingDialogAuth.value)
    }

    @Test
    fun `switching to a remote core follows its status and sends actions there`() =
        runTest(dispatcher.scheduler) {
            val controller = newController()
            targets.value = CoreTarget(coreClient)
            advanceUntilIdle()
            coreClient.openVPNFlow.emit(statusUpdate(challengeId = "local"))
            advanceUntilIdle()

            val remoteClient = FakeCoreClient()
            targets.value = CoreTarget(remoteClient, remoteServerId = 1L)
            advanceUntilIdle()
            assertEquals(emptyList(), controller.endpoints.value)

            remoteClient.openVPNFlow.emit(statusUpdate(challengeId = "remote"))
            advanceUntilIdle()
            assertEquals("remote", controller.pendingDialogAuth.value?.challenge?.id)

            controller.cancelAuthChallenge("home", "remote")
            advanceUntilIdle()
            assertEquals("home" to "remote", remoteClient.lastOpenVPNCancel)
            assertNull(coreClient.lastOpenVPNCancel)
        }

    @Test
    fun `an action without a running core reports an error`() = runTest(dispatcher.scheduler) {
        val controller = newController()

        val error = controller.cancelAuthChallenge("home", "c1")
        advanceUntilIdle()

        assertEquals("core is not running", error)
        assertNull(coreClient.lastOpenVPNCancel)
    }

    private fun newController() = OpenVPNAuthController(
        targets = targets,
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
