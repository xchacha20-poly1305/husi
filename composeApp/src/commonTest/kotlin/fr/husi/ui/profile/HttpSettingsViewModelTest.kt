package fr.husi.ui.profile

import fr.husi.fmt.http.HttpBean
import fr.husi.test.MainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HttpSettingsViewModelTest : MainDispatcherTest() {

    private fun newViewModel() = HttpSettingsViewModel().also {
        it.initialize(editingId = -1L, isSubscription = false)
    }

    @Test
    fun `setSecurity without tls should drop HTTP 3 and version fallback`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setSecurity(TLS)
        viewModel.setHttpVersion(HttpBean.HTTP_VERSION_3)
        viewModel.setDisableVersionFallback(true)
        assertEquals(HttpBean.HTTP_VERSION_3, viewModel.uiState.value.httpVersion)
        assertTrue(viewModel.uiState.value.disableVersionFallback)

        viewModel.setSecurity(NO_SECURITY)

        assertEquals(HttpBean.HTTP_VERSION_2, viewModel.uiState.value.httpVersion)
        assertFalse(viewModel.uiState.value.disableVersionFallback)
    }

    @Test
    fun `setHttpVersion without tls should not select HTTP 3`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setSecurity(NO_SECURITY)
        viewModel.setHttpVersion(HttpBean.HTTP_VERSION_3)

        assertEquals(HttpBean.HTTP_VERSION_2, viewModel.uiState.value.httpVersion)
    }

    @Test
    fun `setDisableVersionFallback without tls should keep fallback`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setSecurity(NO_SECURITY)
        viewModel.setHttpVersion(HttpBean.HTTP_VERSION_2)
        viewModel.setDisableVersionFallback(true)

        assertFalse(viewModel.uiState.value.disableVersionFallback)
    }

    @Test
    fun `supportedHttpVersions should offer HTTP 3 only with tls`() {
        assertEquals(
            listOf(HttpBean.HTTP_VERSION_1, HttpBean.HTTP_VERSION_2, HttpBean.HTTP_VERSION_3),
            HttpBean.supportedHttpVersions(isTLS = true),
        )
        assertEquals(
            listOf(HttpBean.HTTP_VERSION_1, HttpBean.HTTP_VERSION_2),
            HttpBean.supportedHttpVersions(isTLS = false),
        )
    }

    private companion object {
        const val TLS = "tls"
        const val NO_SECURITY = ""
    }
}
