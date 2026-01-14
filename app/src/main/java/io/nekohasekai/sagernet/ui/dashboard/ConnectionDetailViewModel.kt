package io.nekohasekai.sagernet.ui.dashboard

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.toConnectionList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libcore.Client
import libcore.Libcore

@Stable
class ConnectionDetailViewModel : ViewModel() {

    private var client: Client? = null
    private val connectionState = MutableStateFlow(Connection())
    val connection = connectionState.asStateFlow()

    private var job: Job? = null

    override fun onCleared() {
        job?.cancel()
        job = null
        client?.close()
        client = null
        super.onCleared()
    }

    fun initialize(uuid: String) {
        job?.cancel()
        client?.close()
        client = try {
            Libcore.newClient()
        } catch (e: Exception) {
            Logs.w("Failed to create client: ${e.message}")
            null
        }
        job = viewModelScope.launch {
            val interval = DataStore.speedInterval.takeIf { it > 0 }?.toLong() ?: 1000L
            while (isActive) {
                findAndRefresh(uuid)
                delay(interval)
            }
        }
    }

    private suspend inline fun findAndRefresh(uuid: String) {
        if (!DataStore.serviceState.connected) return
        val c = client ?: return
        val connection = try {
            c.queryConnections((Libcore.ShowTrackerActively.toInt() or Libcore.ShowTrackerClosed.toInt()).toByte())
                ?.toConnectionList()
                ?.find { it.uuid == uuid }
        } catch (e: Exception) {
            Logs.w("queryConnections error: ${e.message}")
            null
        } ?: return
        connectionState.value = connection
    }

    fun closeConnection(uuid: String) {
        try {
            client?.closeConnection(uuid)
        } catch (e: Exception) {
            Logs.w("closeConnection error: ${e.message}")
        }
    }
}
