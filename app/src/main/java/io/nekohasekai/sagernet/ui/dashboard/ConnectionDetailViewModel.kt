package io.nekohasekai.sagernet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.bg.SagerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libcore.Libcore

class ConnectionDetailViewModel : ViewModel() {

    private val connectionState = MutableStateFlow(Connection())
    val connection = connectionState.asStateFlow()

    fun initialize(sagerConnection: SagerConnection, uuid: String) = viewModelScope.launch {
        while (isActive) {
            findAndRefresh(sagerConnection, uuid)
        }
    }

    private suspend inline fun findAndRefresh(sagerConnection: SagerConnection, uuid: String) {
        val connection = sagerConnection.service
            ?.queryConnections(Libcore.ShowTrackerActively or Libcore.ShowTrackerClosed)
            ?.connections
            ?.find { it.uuid == uuid }
            ?: return
        connectionState.value = connection
    }
}