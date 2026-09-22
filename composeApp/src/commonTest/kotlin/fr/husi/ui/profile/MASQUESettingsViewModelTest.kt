package fr.husi.ui.profile

import fr.husi.fmt.HttpVersion
import fr.husi.test.MainDispatcherTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class MASQUESettingsViewModelTest : MainDispatcherTest() {

    private fun newViewModel() = MASQUESettingsViewModel().also {
        it.initialize(editingId = -1L, isSubscription = false)
    }

    @Test
    fun `setEnableTLS without tls should drop HTTP 3 and version fallback`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setHttpVersion(HttpVersion.HTTP_3)
        viewModel.setDisableVersionFallback(true)
        assertEquals(HttpVersion.HTTP_3, viewModel.uiState.value.httpVersion)
        assertTrue(viewModel.uiState.value.disableVersionFallback)

        viewModel.setEnableTLS(false)

        assertEquals(HttpVersion.HTTP_2, viewModel.uiState.value.httpVersion)
        assertFalse(viewModel.uiState.value.disableVersionFallback)
    }

    @Test
    fun `setHttpVersion without tls should not select HTTP 3`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setEnableTLS(false)
        viewModel.setHttpVersion(HttpVersion.HTTP_3)

        assertEquals(HttpVersion.HTTP_2, viewModel.uiState.value.httpVersion)
    }

    @Test
    fun `setDisableVersionFallback without tls should keep fallback`() = runTest(dispatcher.scheduler) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setEnableTLS(false)
        viewModel.setHttpVersion(HttpVersion.HTTP_2)
        viewModel.setDisableVersionFallback(true)

        assertFalse(viewModel.uiState.value.disableVersionFallback)
    }
}
