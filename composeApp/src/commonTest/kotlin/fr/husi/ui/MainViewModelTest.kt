package fr.husi.ui

import fr.husi.database.DataStore
import fr.husi.database.GroupManager
import fr.husi.test.HusiKoinMainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest : HusiKoinMainDispatcherTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    override suspend fun preStopKoin() {
        GroupManager.userInterface = null
    }

    /**
     * https://codeberg.org/xchacha20-poly1305/husi/issues/50
     */
    @Test
    fun `confirm should complete false when dialog is dismissed`() = runTest(dispatcher.scheduler) {
        val viewModel = MainViewModel()

        val eventDeferred = backgroundScope.async {
            viewModel.uiEvent.first()
        }
        val resultDeferred = backgroundScope.async {
            viewModel.confirm("confirm")
        }

        advanceUntilIdle()

        val event = assertIs<MainViewModelUiEvent.AlertDialog>(eventDeferred.await())
        event.onDismiss!!.invoke()

        advanceUntilIdle()

        assertFalse(resultDeferred.await())
    }
}
