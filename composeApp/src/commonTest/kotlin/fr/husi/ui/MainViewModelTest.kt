package fr.husi.ui

import fr.husi.bg.AppUpdateAutoChecker
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.database.DataStore
import fr.husi.test.FakeAppUpdateFetcher
import fr.husi.test.HusiKoinMainDispatcherTest
import fr.husi.test.appUpdateInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest : HusiKoinMainDispatcherTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    @AfterTest
    fun resetBackendState() {
        BackendState.reset()
    }

    /**
     * https://codeberg.org/xchacha20-poly1305/husi/issues/50
     */
    @Test
    fun `confirm should complete false when dialog is dismissed`() = runTest(dispatcher.scheduler) {
        val viewModel = MainViewModel()

        val eventDeferred = backgroundScope.async {
            viewModel.dialogEvent.first()
        }
        val resultDeferred = backgroundScope.async {
            viewModel.confirm("confirm")
        }

        advanceUntilIdle()

        val event = assertIs<MainAlertDialogEvent>(eventDeferred.await())
        event.onDismiss!!.invoke()

        advanceUntilIdle()

        assertFalse(resultDeferred.await())
    }


    @Test
    fun `app update check waits for the service to connect`() = runTest(dispatcher.scheduler) {
        DataStore.appUpdateAutoCheck.set(true)
        DataStore.appUpdateOnlyWhenConnected.set(true)
        DataStore.appUpdateLastCheckEpochDay.set(0L)
        DataStore.appUpdateSkippedVersion.set("")
        val info = appUpdateInfo()
        val fetcher = FakeAppUpdateFetcher(nextResult = info)
        val viewModel = MainViewModel(
            appUpdateChecker = AppUpdateAutoChecker(
                fetcher = fetcher,
                isSupported = true,
                today = { 100L },
                ioDispatcher = dispatcher,
            ),
        )

        advanceUntilIdle()
        assertEquals(0, fetcher.checkCount)
        assertNull(viewModel.appUpdate.value)

        BackendState.updateState(ServiceState.Connected)
        advanceUntilIdle()

        assertEquals(1, fetcher.checkCount)
        assertEquals(info.version, viewModel.appUpdate.value?.version)

        viewModel.close()
    }
}
