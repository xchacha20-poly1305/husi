package fr.husi.vpn

import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.test.MainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class VpnAuthSessionTest : MainDispatcherTest() {

    @AfterTest
    fun resetBackendState() {
        BackendState.reset()
    }

    @Test
    fun `firstVpnAuthPending skips non-pending endpoints`() {
        val pending = firstVpnAuthPending(
            endpoints = listOf(
                FakeEndpoint("a", VPN_ENDPOINT_STATE_CONNECTING, "old"),
                FakeEndpoint("b", VPN_ENDPOINT_STATE_AUTH_PENDING, "c1"),
                FakeEndpoint("c", VPN_ENDPOINT_STATE_AUTH_PENDING, "c2"),
            ),
            state = { it.state },
            challengeId = { it.challengeId },
            tag = { it.tag },
        )
        assertEquals(VpnAuthPendingNotice("b", "c1"), pending)
    }

    @Test
    fun `dismissed challenge stays hidden until a new id arrives`() = runTest(dispatcher.scheduler) {
        val updates = MutableSharedFlow<List<FakeEndpoint>>(replay = 1)
        val session = VpnAuthSession(
            subscribe = { updates },
            pendingOf = { endpoint ->
                endpoint.challengeId?.takeIf {
                    endpoint.state == VPN_ENDPOINT_STATE_AUTH_PENDING
                }?.let { PendingVpnAuth(endpoint.tag, it) }
            },
            challengeId = { it },
            logLabel = "test",
            dispatcher = dispatcher,
        )
        BackendState.updateState(ServiceState.Connected)
        advanceUntilIdle()

        updates.emit(
            listOf(FakeEndpoint("home", VPN_ENDPOINT_STATE_AUTH_PENDING, "c1")),
        )
        advanceUntilIdle()
        assertEquals("c1", session.pendingDialogAuth.value?.challenge)

        session.dismissDialog("home", "c1")
        advanceUntilIdle()
        assertNull(session.pendingDialogAuth.value)

        updates.emit(
            listOf(FakeEndpoint("home", VPN_ENDPOINT_STATE_AUTH_PENDING, "c2")),
        )
        advanceUntilIdle()
        assertEquals("c2", session.pendingDialogAuth.value?.challenge)
    }

    private data class FakeEndpoint(
        val tag: String,
        val state: String,
        val challengeId: String?,
    )
}
