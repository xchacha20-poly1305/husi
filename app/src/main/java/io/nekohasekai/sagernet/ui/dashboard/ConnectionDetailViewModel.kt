package io.nekohasekai.sagernet.ui.dashboard

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libcore.Libcore

@Stable
class ConnectionDetailViewModel : ViewModel() {

    private lateinit var sagerConnection: SagerConnection
    private val connectionState = MutableStateFlow(Connection())
    val connection = connectionState.asStateFlow()

    private var job: Job? = null

    override fun onCleared() {
        job?.cancel()
        job = null
        super.onCleared()
    }

    fun initialize(sagerConnection: SagerConnection, uuid: String)  {
        job?.cancel()
        this.sagerConnection = sagerConnection
        job = viewModelScope.launch {
            val interval = DataStore.speedInterval.takeIf { it > 0 }?.toLong() ?: 1000L
            while (isActive) {
                findAndRefresh(uuid)
                delay(interval)
            }
        }
    }

    private suspend inline fun findAndRefresh(uuid: String) {
        val connection = sagerConnection.service.value
            ?.queryConnections(Libcore.ShowTrackerActively or Libcore.ShowTrackerClosed)
            ?.connections
            ?.find { it.uuid == uuid }
            ?: return
        connectionState.value = connection
    }

    fun closeConnection(uuid: String) {
        sagerConnection.service.value?.closeConnection(uuid)
    }
}