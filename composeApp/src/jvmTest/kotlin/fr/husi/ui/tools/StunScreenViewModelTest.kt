package fr.husi.ui.tools

import fr.husi.core.NatBehaviour
import fr.husi.core.StunPhase
import fr.husi.proto.daemon.STUNTestProgress
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinMainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StunScreenViewModelTest : HusiKoinMainDispatcherTest() {

    private val fakeCore = FakeCoreClient()

    private fun newViewModel() = StunScreenViewModel(
        coreClient = fakeCore,
        ioDispatcher = dispatcher,
    )

    @Test
    fun `doTest routes through the chosen outbound while the service runs`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()
            viewModel.setServer("stun.example:3478")
            viewModel.setOutboundTag("proxy")

            viewModel.doTest(serviceRunning = true)
            advanceUntilIdle()

            val call = assertNotNull(fakeCore.lastStunTest)
            assertFalse(call.standalone)
            assertEquals("stun.example:3478", call.server)
            assertEquals("proxy", call.outboundTag)
        }

    @Test
    fun `doTest dials directly when no service is running`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        // A tag left over from an earlier run must not reach the standalone RPC,
        // which has no outbound to apply it to.
        viewModel.setOutboundTag("proxy")

        viewModel.doTest(serviceRunning = false)
        advanceUntilIdle()

        val call = assertNotNull(fakeCore.lastStunTest)
        assertTrue(call.standalone)
        assertEquals("", call.outboundTag)
    }

    @Test
    fun `a final progress builds the report`() = runTest(dispatcher.scheduler) {
        fakeCore.stunTestProgresses = listOf(
            STUNTestProgress.newBuilder()
                .setPhase(StunPhase.Done.ordinal)
                .setExternalAddr("203.0.113.7:14000")
                .setLatencyMs(42)
                .setNatMapping(2)
                .setNatFiltering(1)
                .setNatTypeSupported(true)
                .setIsFinal(true)
                .build(),
        )
        val viewModel = newViewModel()

        viewModel.doTest(serviceRunning = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isDoing)
        val report = assertNotNull(state.report)
        assertEquals(StunPhase.Done, report.phase)
        assertEquals("203.0.113.7:14000", report.externalAddress)
        assertEquals(42, report.latencyMs)
        assertEquals(NatBehaviour.EndpointIndependent, report.mapping)
        assertEquals(NatBehaviour.EndpointIndependent, report.filtering)
        assertFalse(report.natTypeUnsupported)
    }

    // sing-box reports a failed test in the stream rather than by failing the
    // RPC, so a plain `catch` would never see it.
    @Test
    fun `an in-stream error alerts and stops the test`() = runTest(dispatcher.scheduler) {
        fakeCore.stunTestProgresses = listOf(
            STUNTestProgress.newBuilder()
                .setIsFinal(true)
                .setError("binding request: timeout")
                .build(),
        )
        val viewModel = newViewModel()

        val event = backgroundScope.async { viewModel.uiEvent.first() }
        viewModel.doTest(serviceRunning = false)
        advanceUntilIdle()

        val alert = assertIs<StunScreenUiEvent.Alert>(event.await())
        assertEquals("binding request: timeout", alert.message)
        assertFalse(viewModel.uiState.value.isDoing)
    }
}
