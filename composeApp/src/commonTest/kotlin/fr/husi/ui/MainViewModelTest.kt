package fr.husi.ui

import fr.husi.database.DataStore
import fr.husi.database.GroupManager
import fr.husi.di.initHusiKoin
import fr.husi.repository.FakeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        initHusiKoin(FakeRepository())
        DataStore.configurationStore.reset()
    }

    @AfterTest
    fun tearDown() {
        GroupManager.userInterface = null
        stopKoin()
        Dispatchers.resetMain()
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
