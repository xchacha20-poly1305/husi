package fr.husi.tailscale

import fr.husi.core.remote.CoreTarget
import fr.husi.proto.daemon.TailscaleSSHClientMessage
import fr.husi.proto.daemon.tailscaleSSHError
import fr.husi.proto.daemon.tailscaleSSHReady
import fr.husi.proto.daemon.tailscaleSSHServerMessage
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinMainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TailscaleSSHSessionManagerTest : HusiKoinMainDispatcherTest() {

    private val target = TailscaleSSHTarget(
        endpointTag = "ts",
        peerStableId = "peer-stable",
        peerHostName = "laptop",
        peerAddress = "100.64.0.2",
        hostKeys = listOf("ssh-ed25519 AAAA"),
        login = TailscaleSSHLogin(username = "alice", terminalType = "xterm-ghostty"),
    )

    // Every test closes its sessions: the manager runs them in the test's own scope.
    private fun TestScope.newManager(client: FakeCoreClient?) = TailscaleSSHSessionManager(
        targets = MutableStateFlow(client?.let { CoreTarget(it) }),
        scope = this,
    )

    @Test
    fun `open sends the start message first`() = runTest(dispatcher.scheduler) {
        val client = FakeCoreClient()
        val manager = newManager(client)

        manager.open(target)
        advanceUntilIdle()

        val start = client.tailscaleSSHRequests.first()
        assertEquals(TailscaleSSHClientMessage.MessageCase.START, start.messageCase)
        assertEquals("ts", start.start.endpointTag)
        assertEquals("100.64.0.2", start.start.peerAddress)
        assertEquals("alice", start.start.username)
        assertEquals("xterm-ghostty", start.start.terminalType)
        assertEquals(listOf("ssh-ed25519 AAAA"), start.start.hostKeysList)
        assertEquals(80, start.start.columns)
        assertEquals(24, start.start.rows)
        manager.closeAll()
    }

    @Test
    fun `ready marks the session running`() = runTest(dispatcher.scheduler) {
        val client = FakeCoreClient().also {
            it.tailscaleSSHResponses = listOf(tailscaleSSHServerMessage { ready = tailscaleSSHReady { } })
        }
        val manager = newManager(client)

        manager.open(target)
        advanceUntilIdle()

        assertEquals(TailscaleSSHPhase.RUNNING, manager.state.value.activeSession?.phase?.value)
        manager.closeAll()
    }

    @Test
    fun `an error from the core finishes the session as failed`() = runTest(dispatcher.scheduler) {
        val client = FakeCoreClient().also {
            it.tailscaleSSHResponses = listOf(
                tailscaleSSHServerMessage { error = tailscaleSSHError { message = "untrusted host key" } },
            )
        }
        val manager = newManager(client)

        manager.open(target)
        advanceUntilIdle()

        val session = manager.state.value.sessions.single()
        assertEquals(TailscaleSSHPhase.FINISHED, session.phase.value)
        assertEquals(TailscaleSSHSession.SESSION_FAILED, session.terminal.exitStatus.value)
        manager.closeAll()
    }

    @Test
    fun `open without a running core finishes the session as failed`() = runTest(dispatcher.scheduler) {
        val manager = newManager(client = null)

        manager.open(target)
        advanceUntilIdle()

        val session = manager.state.value.sessions.single()
        assertEquals(TailscaleSSHPhase.FINISHED, session.phase.value)
        assertEquals(TailscaleSSHSession.SESSION_FAILED, session.terminal.exitStatus.value)
        manager.closeAll()
    }

    @Test
    fun `closing the active session activates the last one left`() = runTest(dispatcher.scheduler) {
        val manager = newManager(FakeCoreClient())

        val first = manager.open(target)
        val second = manager.open(target)
        val third = manager.open(target)
        manager.select(second)
        manager.close(second)

        assertEquals(listOf(first, third), manager.state.value.sessions.map { it.id })
        assertEquals(third, manager.state.value.activeSessionId)

        manager.closeAll()
        assertEquals(emptyList(), manager.state.value.sessions)
        assertNull(manager.state.value.activeSessionId)
    }

    @Test
    fun `duplicate opens another session with the same target`() = runTest(dispatcher.scheduler) {
        val manager = newManager(FakeCoreClient())

        val first = manager.open(target)
        val second = manager.open(target).also { manager.select(first) }
        manager.duplicate(first)

        val sessions = manager.state.value.sessions
        assertEquals(3, sessions.size)
        assertEquals(target, sessions.last().target)
        assertEquals(sessions.last().id, manager.state.value.activeSessionId)
        assertEquals(listOf(first, second), sessions.take(2).map { it.id })
        manager.closeAll()
    }
}
