package fr.husi.tailscale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.remote.CoreTarget
import fr.husi.ktx.readableMessage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TaildropInboxUiState(
    val files: List<TaildropFileState> = emptyList(),
    val receiving: List<TaildropReceivingFileState> = emptyList(),
    val filesDirectory: TaildropFilesDirectory? = null,
)

data class TaildropFilesDirectory(
    val directory: File,
    val isDownloads: Boolean,
)

class TaildropInboxViewModel(
    private val endpointTag: String,
    repository: TailscaleStatusRepository,
    private val localReceiveDirectory: File?,
    private val downloadDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val uiState: StateFlow<TaildropInboxUiState>
        field = MutableStateFlow(TaildropInboxUiState())

    val closed: SharedFlow<Unit>
        field = MutableSharedFlow()

    val uiEvent: SharedFlow<TailscaleUiEvent>
        field = MutableSharedFlow()

    private val presence = TailscalePresence.endpoint(endpointTag)

    init {
        viewModelScope.launch {
            repository.snapshot.collect { snapshot ->
                if (presence.shouldClose(snapshot)) {
                    closed.emit(Unit)
                }
            }
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                // The same first core that presence binds to; another core closes the screen.
                val target = repository.snapshot.mapNotNull { it.target }.first()
                uiState.update { it.copy(filesDirectory = filesDirectoryOf(target)) }
                target.client.subscribeTaildropInbox(endpointTag).collect { inbox ->
                    val files = inbox.filesList.map { it.toState() }
                    val receiving = inbox.receivingList.map { it.toState() }
                    uiState.update { it.copy(files = files, receiving = receiving) }
                    if (files.isNotEmpty()) {
                        try {
                            target.client.markTaildropInboxRead(endpointTag)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            uiEvent.emit(TailscaleUiEvent.Message(e.readableMessage))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiEvent.emit(TailscaleUiEvent.Message(e.readableMessage))
            }
        }
    }

    fun delete(name: String) = launchAction {
        presence.requireTarget().client.deleteTaildropFile(endpointTag, name)
    }

    fun deleteAll() = launchAction {
        val client = presence.requireTarget().client
        val names = uiState.value.files.map { it.name }
        for (name in names) {
            client.deleteTaildropFile(endpointTag, name)
        }
    }

    fun cancelReceiving(senderId: String, name: String) = launchAction {
        presence.requireTarget().client.cancelTaildropReceiving(endpointTag, senderId, name)
    }

    suspend fun materialize(name: String): File = withContext(ioDispatcher) {
        val target = presence.requireTarget()
        materializeTaildropFile(
            coreClient = target.client,
            receiveDirectory = receiveDirectoryOf(target),
            downloadDirectory = downloadDirectory,
            endpointTag = endpointTag,
            name = name,
        )
    }

    private fun receiveDirectoryOf(target: CoreTarget): File? =
        if (target.isRemote) null else localReceiveDirectory

    private fun filesDirectoryOf(target: CoreTarget): TaildropFilesDirectory =
        receiveDirectoryOf(target)
            ?.let { directory -> TaildropFilesDirectory(directory, isDownloads = false) }
            ?: TaildropFilesDirectory(downloadDirectory, isDownloads = true)

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiEvent.emit(TailscaleUiEvent.Message(e.readableMessage))
            }
        }
    }
}
