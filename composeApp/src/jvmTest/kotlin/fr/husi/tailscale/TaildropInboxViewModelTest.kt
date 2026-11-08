package fr.husi.tailscale

import fr.husi.core.remote.CoreTarget
import fr.husi.proto.daemon.taildropFile
import fr.husi.proto.daemon.taildropInbox
import fr.husi.proto.daemon.tailscaleEndpointStatus
import fr.husi.proto.daemon.tailscaleStatusUpdate
import fr.husi.test.FakeCoreClient
import fr.husi.test.MainDispatcherTest
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class TaildropInboxViewModelTest : MainDispatcherTest() {

    @Test
    fun `a non-empty inbox is marked read`() = runTest(dispatcher.scheduler) {
        val client = FakeCoreClient()
        val viewModel = newViewModel(client)

        client.taildropInboxFlow.emit(
            taildropInbox {
                endpointTag = "ts"
                files.add(
                    taildropFile {
                        name = "a.txt"
                        size = 3L
                        senderName = "laptop"
                    },
                )
            },
        )
        advanceUntilIdle()

        assertEquals("ts", client.lastTaildropInboxRead)
        assertEquals("a.txt", viewModel.uiState.value.files.single().name)
    }

    @Test
    fun `an empty inbox is not marked read`() = runTest(dispatcher.scheduler) {
        val client = FakeCoreClient()
        newViewModel(client)

        client.taildropInboxFlow.emit(taildropInbox { endpointTag = "ts" })
        advanceUntilIdle()

        assertNull(client.lastTaildropInboxRead)
    }

    @Test
    fun `a failed mark read does not stop the inbox subscription`() = runTest(dispatcher.scheduler) {
        var marks = 0
        val client = object : FakeCoreClient() {
            override suspend fun markTaildropInboxRead(tag: String) {
                marks += 1
                lastTaildropInboxRead = tag
                if (marks == 1) error("mark failed")
            }
        }
        val viewModel = newViewModel(client)
        val messages = mutableListOf<TailscaleUiEvent>()
        // Foreground collector: advanceUntilIdle does not run backgroundScope once
        // the inbox job is suspended, and a replay-0 SharedFlow drops events with no collector.
        val collecting = launch { viewModel.uiEvent.collect { event -> messages += event } }
        advanceUntilIdle()

        client.taildropInboxFlow.emit(
            taildropInbox {
                endpointTag = "ts"
                files.add(taildropFile { name = "a.txt" })
            },
        )
        advanceUntilIdle()

        assertEquals(1, marks)
        assertIs<TailscaleUiEvent.Message>(messages.single())

        client.taildropInboxFlow.emit(
            taildropInbox {
                endpointTag = "ts"
                files.add(taildropFile { name = "b.txt" })
            },
        )
        advanceUntilIdle()
        collecting.cancel()

        assertEquals(2, marks)
        assertEquals("b.txt", viewModel.uiState.value.files.single().name)
    }

    @Test
    fun `a remote inbox keeps its files in the download directory`() = runTest(dispatcher.scheduler) {
        val client = FakeCoreClient()
        val viewModel = newViewModel(client, CoreTarget(client, remoteServerId = 1L))
        advanceUntilIdle()

        assertEquals(
            TaildropFilesDirectory(DOWNLOAD_DIRECTORY, isDownloads = true),
            viewModel.uiState.value.filesDirectory,
        )
        assertEquals("ts", client.lastTaildropInboxTag)
    }

    @Test
    fun `delete goes to the core that reported the endpoint`() = runTest(dispatcher.scheduler) {
        val client = FakeCoreClient()
        val viewModel = newViewModel(client)
        advanceUntilIdle()
        assertEquals(
            TaildropFilesDirectory(RECEIVE_DIRECTORY, isDownloads = false),
            viewModel.uiState.value.filesDirectory,
        )

        viewModel.delete("a.txt")
        advanceUntilIdle()

        assertEquals("ts" to "a.txt", client.lastDeletedTaildropFile)
    }

    private fun newViewModel(
        client: FakeCoreClient,
        target: CoreTarget = CoreTarget(client),
    ): TaildropInboxViewModel {
        client.tailscaleStatusFlow.tryEmit(
            tailscaleStatusUpdate {
                endpoints.add(tailscaleEndpointStatus { endpointTag = "ts" })
            },
        )
        return TaildropInboxViewModel(
            endpointTag = "ts",
            // Not backgroundScope: advanceUntilIdle stops once only background work is left.
            repository = TailscaleStatusRepository(MutableStateFlow(target), CoroutineScope(dispatcher)),
            localReceiveDirectory = RECEIVE_DIRECTORY,
            downloadDirectory = DOWNLOAD_DIRECTORY,
            ioDispatcher = dispatcher,
        )
    }
}

private val RECEIVE_DIRECTORY = File("receive")
private val DOWNLOAD_DIRECTORY = File("downloads")
