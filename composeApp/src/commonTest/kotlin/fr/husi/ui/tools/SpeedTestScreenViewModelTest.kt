package fr.husi.ui.tools

import fr.husi.SPEED_TEST_UPLOAD_URL
import fr.husi.SPEED_TEST_URL
import fr.husi.bg.ServiceState
import fr.husi.database.DataStore
import fr.husi.test.HusiHttpKoinTest
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
class SpeedTestScreenViewModelTest : HusiHttpKoinTest() {

    @AfterTest
    fun resetServiceState() {
        DataStore.serviceState = ServiceState.Idle
    }

    private fun newViewModel() = SpeedTestScreenViewModel(
        httpClientFactory = fakeHttp,
        ioDispatcher = dispatcher,
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

        val client = assertNotNull(fakeHttp.lastClient, "an HTTP client should have been created")
        val request = assertNotNull(client.lastRequest, "a request should have been issued")
        assertEquals(SPEED_TEST_URL, request.url)
        assertEquals(fakeHttp.userAgent, request.userAgent)
        assertEquals(20000, request.timeout)
        assertEquals("http://speed.cloudflare.com/", request.headers["Referer"])
        assertNull(client.socks5, "socks5 should be untouched when service is idle")

        val finalState = viewModel.uiState.value
        assertTrue(finalState.canTest)
        assertNull(finalState.progress)
        assertTrue(finalState.speed > 0L, "speed should have been updated by CopyCallback")
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
    fun `doSpeedTest upload sends setContentZero with configured length`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        viewModel.setMode(SpeedTestScreenViewModel.SpeedTestMode.Upload)
        viewModel.setUploadSize("4096")
        advanceUntilIdle()

        viewModel.doSpeedTest()
        advanceUntilIdle()

        val request = assertNotNull(fakeHttp.lastClient?.lastRequest)
        val contentZero = assertNotNull(request.contentZero, "upload must drive setContentZero")
        assertEquals(4096L, contentZero.length)
        assertEquals(SPEED_TEST_UPLOAD_URL, request.url)
        assertTrue(viewModel.uiState.value.speed > 0L)
    }

    @Test
    fun `doSpeedTest emits ErrorAlert when execute throws`() = runTest(dispatcher.scheduler) {
        fakeHttp.nextThrowable = IOException("boom")
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
    fun `doSpeedTest configures socks5 when service is connected`() = runTest(dispatcher.scheduler) {
        DataStore.inboundUsername = "user"
        DataStore.inboundPassword = "pass"
        DataStore.serviceState = ServiceState.Connected
        val viewModel = newViewModel()

        viewModel.doSpeedTest()
        advanceUntilIdle()

        val client = assertNotNull(fakeHttp.lastClient)
        val socks5 = assertNotNull(client.socks5, "useSocks5 must be called when connected")
        assertEquals(2080, socks5.port)
        assertEquals("user", socks5.username)
        assertEquals("pass", socks5.password)
    }

    @Test
    fun `doSpeedTest closes the previous response on re-entry`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()

        viewModel.doSpeedTest()
        advanceUntilIdle()
        val firstResponse = assertNotNull(fakeHttp.lastClient?.lastRequest?.lastResponse)
        val closedBefore = firstResponse.closed

        viewModel.doSpeedTest()
        advanceUntilIdle()

        assertTrue(
            firstResponse.closed > closedBefore,
            "calling doSpeedTest again must close the previous response",
        )
        assertEquals(2, fakeHttp.clients.size)
    }
}
