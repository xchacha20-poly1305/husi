package fr.husi.ui.tools

import fr.husi.SPEED_TEST_UPLOAD_URL
import fr.husi.SPEED_TEST_URL
import fr.husi.bg.ServiceState
import fr.husi.database.DataStore
import fr.husi.proto.v1.SpeedTestMode
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinMainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedTestScreenViewModelTest : HusiKoinMainDispatcherTest() {

    private val fakeCore = FakeCoreClient()

    @AfterTest
    fun resetServiceState() {
        DataStore.serviceState = ServiceState.Idle
        DataStore.inboundUsername = ""
        DataStore.inboundPassword = ""
        fakeCore.speedTestThrowable = null
        fakeCore.speedTestCalls = 0
        fakeCore.lastSpeedTest = null
    }

    private fun newViewModel() = SpeedTestScreenViewModel(
        coreClient = fakeCore,
        ioDispatcher = dispatcher,
        userAgent = "husi-test/0",
    )

    @Test
    fun `initialize fills state from DataStore defaults`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()

        val state = viewModel.uiState.value
        assertEquals(SPEED_TEST_URL, state.downloadURL)
        assertEquals(SPEED_TEST_UPLOAD_URL, state.uploadURL)
        assertEquals(20000, state.timeout)
        assertEquals(10L * 1024L * 1024L, state.uploadLength)
        assertEquals(SpeedTestScreenViewModel.SpeedTestMode.Download, state.mode)
    }

    @Test
    fun `setServer with blank value sets urlError`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()

        viewModel.setServer("   ")
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.urlError)
    }

    @Test
    fun `setServer writes to the URL for the current mode`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()

        viewModel.setServer("http://example.com/down")
        advanceUntilIdle()
        assertEquals("http://example.com/down", viewModel.uiState.value.downloadURL)
        assertEquals(SPEED_TEST_UPLOAD_URL, viewModel.uiState.value.uploadURL)

        viewModel.setMode(SpeedTestScreenViewModel.SpeedTestMode.Upload)
        viewModel.setServer("http://example.com/up")
        advanceUntilIdle()
        assertEquals("http://example.com/up", viewModel.uiState.value.uploadURL)
        assertEquals("http://example.com/down", viewModel.uiState.value.downloadURL)
    }

    @Test
    fun `setTimeout with non-numeric input sets timeoutError`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()

        viewModel.setTimeout("abc")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.timeoutError)

        viewModel.setTimeout("5000")
        advanceUntilIdle()
        assertEquals(5000, viewModel.uiState.value.timeout)
    }

    @Test
    fun `setUploadSize with non-numeric input sets uploadLengthError`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()

        viewModel.setUploadSize("oops")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.uploadLengthError)

        viewModel.setUploadSize("4096")
        advanceUntilIdle()
        assertEquals(4096L, viewModel.uiState.value.uploadLength)
    }

    @Test
    fun `doSpeedTest download issues a properly configured request`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()

        viewModel.doSpeedTest()
        advanceUntilIdle()

        val call = assertNotNull(fakeCore.lastSpeedTest, "speedTest RPC should have been called")
        assertEquals(SpeedTestMode.SPEED_TEST_MODE_DOWNLOAD, call.mode)
        assertEquals(SPEED_TEST_URL, call.url)
        assertEquals("husi-test/0", call.userAgent)
        assertEquals(20000, call.timeoutMs)
        assertEquals("", call.socksProxyUrl, "proxy empty when service is idle")

        val finalState = viewModel.uiState.value
        assertTrue(finalState.canTest)
        assertNull(finalState.progress)
        assertTrue(finalState.speed > 0L, "speed should have been updated from stream")
    }

    @Test
    fun `doSpeedTest download emits done snackbar`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()

        val event = backgroundScope.async { viewModel.uiEvent.first() }

        viewModel.doSpeedTest()
        advanceUntilIdle()

        assertIs<SpeedTestScreenUiEvent.Snackbar>(event.await())
    }

    @Test
    fun `doSpeedTest upload sends upload length`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        viewModel.setMode(SpeedTestScreenViewModel.SpeedTestMode.Upload)
        viewModel.setUploadSize("4096")
        advanceUntilIdle()

        viewModel.doSpeedTest()
        advanceUntilIdle()

        val call = assertNotNull(fakeCore.lastSpeedTest)
        assertEquals(SpeedTestMode.SPEED_TEST_MODE_UPLOAD, call.mode)
        assertEquals(4096L, call.uploadLengthBytes)
        assertEquals(SPEED_TEST_UPLOAD_URL, call.url)
        assertTrue(viewModel.uiState.value.speed > 0L)
    }

    @Test
    fun `doSpeedTest emits ErrorAlert when stream fails`() = runTest(dispatcher.scheduler) {
        fakeCore.speedTestThrowable = IOException("boom")
        val viewModel = newViewModel()

        val event = backgroundScope.async { viewModel.uiEvent.first() }

        viewModel.doSpeedTest()
        advanceUntilIdle()

        assertIs<SpeedTestScreenUiEvent.ErrorAlert>(event.await())
        assertTrue(
            viewModel.uiState.value.canTest,
            "canTest must be restored after a failed run",
        )
        assertNull(viewModel.uiState.value.progress)
    }

    @Test
    fun `doSpeedTest passes socks proxy when service is connected`() = runTest(dispatcher.scheduler) {
        DataStore.inboundUsername = "user"
        DataStore.inboundPassword = "pass"
        DataStore.serviceState = ServiceState.Connected
        val viewModel = newViewModel()

        viewModel.doSpeedTest()
        advanceUntilIdle()

        val call = assertNotNull(fakeCore.lastSpeedTest)
        assertTrue(call.socksProxyUrl.isNotEmpty(), "socks proxy URL must be set when connected")
        assertTrue(call.socksProxyUrl.contains("2080"), "proxy URL should include mixed port")
        assertTrue(call.socksProxyUrl.contains("user"), "proxy URL should include username")
    }

    @Test
    fun `doSpeedTest cancels previous stream on re-entry`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()

        viewModel.doSpeedTest()
        advanceUntilIdle()
        assertEquals(1, fakeCore.speedTestCalls)

        viewModel.doSpeedTest()
        advanceUntilIdle()

        assertEquals(2, fakeCore.speedTestCalls)
        assertTrue(viewModel.uiState.value.canTest)
    }
}
