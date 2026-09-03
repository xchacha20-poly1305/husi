package fr.husi.ui.tools

import fr.husi.core.NetworkQualityPhase
import fr.husi.proto.daemon.NetworkQualityTestProgress
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinMainDispatcherTest
import fr.husi.ui.StringOrRes
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
class NetworkQualityScreenViewModelTest : HusiKoinMainDispatcherTest() {

    private val fakeCore = FakeCoreClient()

    private fun newViewModel() = NetworkQualityScreenViewModel(
        coreClient = fakeCore,
        ioDispatcher = dispatcher,
    )

    @Test
    fun `doTest routes through the chosen outbound while the service runs`() =
        runTest(dispatcher.scheduler) {
            val viewModel = newViewModel()
            viewModel.setConfigUrl("https://example.invalid/config")
            viewModel.setOutboundTag("proxy")
            viewModel.setSerial(true)
            viewModel.setHttp3(true)
            viewModel.setMaxRuntimeSeconds("15")
            advanceUntilIdle()

            viewModel.doTest(serviceRunning = true)
            advanceUntilIdle()

            val call = assertNotNull(fakeCore.lastNetworkQualityTest)
            assertFalse(call.standalone)
            assertEquals("https://example.invalid/config", call.configUrl)
            assertEquals("proxy", call.outboundTag)
            assertTrue(call.serial)
            assertTrue(call.http3)
            assertEquals(15, call.maxRuntimeSeconds)
        }

    @Test
    fun `doTest dials directly when no service is running`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        // A tag left over from an earlier run must not reach the standalone RPC,
        // which has no outbound to apply it to.
        viewModel.setOutboundTag("proxy")
        advanceUntilIdle()

        viewModel.doTest(serviceRunning = false)
        advanceUntilIdle()

        val call = assertNotNull(fakeCore.lastNetworkQualityTest)
        assertTrue(call.standalone)
        assertEquals("", call.outboundTag)
    }

    @Test
    fun `a final progress builds the report`() = runTest(dispatcher.scheduler) {
        fakeCore.networkQualityTestProgresses = listOf(
            NetworkQualityTestProgress.newBuilder()
                .setPhase(NetworkQualityPhase.Done.ordinal)
                .setDownloadCapacity(12_000_000)
                .setUploadCapacity(3_000_000)
                .setDownloadRPM(900)
                .setUploadRPM(700)
                .setIdleLatencyMs(18)
                .setElapsedMs(9_000)
                .setIsFinal(true)
                .build(),
        )
        val viewModel = newViewModel()

        viewModel.doTest(serviceRunning = false)
        advanceUntilIdle()

        val report = assertNotNull(viewModel.uiState.value.report)
        assertEquals(NetworkQualityPhase.Done, report.phase)
        assertEquals(12_000_000L, report.downloadCapacity)
        assertEquals(3_000_000L, report.uploadCapacity)
        assertEquals(900, report.downloadRpm)
        assertEquals(700, report.uploadRpm)
        assertEquals(18, report.idleLatencyMs)
        assertEquals(9_000L, report.elapsedMs)
        assertTrue(viewModel.uiState.value.canTest)
    }

    // sing-box reports a failed test in the stream rather than by failing the
    // RPC, so a plain `catch` would never see it.
    @Test
    fun `an in-stream error alerts instead of reporting success`() =
        runTest(dispatcher.scheduler) {
                fakeCore.networkQualityTestProgresses = listOf(
                NetworkQualityTestProgress.newBuilder()
                    .setIsFinal(true)
                    .setError("fetch config: timeout")
                    .build(),
            )
            val viewModel = newViewModel()

            val event = backgroundScope.async { viewModel.uiEvent.first() }
            viewModel.doTest(serviceRunning = false)
            advanceUntilIdle()

            val alert = assertIs<NetworkQualityScreenUiEvent.ErrorAlert>(event.await())
            val message = assertIs<StringOrRes.Direct>(alert.message)
            assertEquals("fetch config: timeout", message.value)
            assertTrue(viewModel.uiState.value.canTest)
        }
}
