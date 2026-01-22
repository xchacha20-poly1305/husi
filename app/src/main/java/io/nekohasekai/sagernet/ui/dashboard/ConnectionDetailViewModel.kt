package io.nekohasekai.sagernet.ui.dashboard

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.utils.LibcoreClientManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import libcore.ConnectionEvent
import libcore.Libcore
import kotlin.experimental.or

@Stable
class ConnectionDetailViewModel : ViewModel() {

    private val clientManager = LibcoreClientManager()
    private val connectionState = MutableStateFlow(ConnectionDetailState())
    val connection = connectionState.asStateFlow()

    private var job: Job? = null

    override fun onCleared() {
        job?.cancel()
        job = null
        runBlocking {
            clientManager.close()
        }
        super.onCleared()
    }

    suspend fun initialize(uuid: String) {
        if (connectionState.value.uuid == uuid)
        job?.cancel()
        connectionState.value = queryConnection(uuid)
        job = clientManager.subscribeConnectionEvents(viewModelScope) { event ->
            handleConnectionEvent(event)
        }
    }

    private suspend fun queryConnection(uuid: String): ConnectionDetailState {
        return try {
            clientManager.withClient { client ->
                val flag = Libcore.ShowTrackerActively or Libcore.ShowTrackerClosed
                val iterator = client.queryConnections(flag)
                    ?: return@withClient ConnectionDetailState(uuid = uuid)
                while (iterator.hasNext()) {
                    val info = iterator.next() ?: continue
                    if (info.uuid == uuid) return@withClient info.toDetailState()
                }
                ConnectionDetailState(uuid = uuid)
            }
        } catch (e: Exception) {
            Logs.w("query connection", e)
            ConnectionDetailState(uuid = uuid)
        }
    }

    private fun handleConnectionEvent(event: ConnectionEvent) {
        if (event.id != connectionState.value.uuid) return
        when (event.type) {
            Libcore.ConnectionEventUpdate -> {
                updateTraffic(event.uplinkDelta, event.downlinkDelta)
            }

            Libcore.ConnectionEventNew -> {
                val trackerInfo = event.trackerInfo ?: return
                connectionState.value = trackerInfo.toDetailState()
            }

            Libcore.ConnectionEventClosed -> {
                if (event.closedAt.isBlank()) return
                updateClosedAt(event.closedAt)
            }
        }
    }

    private fun updateTraffic(uplinkDelta: Long, downlinkDelta: Long) {
        if (uplinkDelta == 0L && downlinkDelta == 0L) return
        val current = connectionState.value
        connectionState.value = current.copy(
            uploadTotal = current.uploadTotal + uplinkDelta,
            downloadTotal = current.downloadTotal + downlinkDelta,
        )
    }

    private fun updateClosedAt(closedAt: String) {
        val current = connectionState.value
        if (current.closedAt == closedAt) return
        connectionState.value = current.copy(closedAt = closedAt)
    }

    fun closeConnection(uuid: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            clientManager.withClient { client ->
                client.closeConnection(uuid)
            }
        } catch (e: Exception) {
            Logs.w("close connection", e)
        }
    }
}
