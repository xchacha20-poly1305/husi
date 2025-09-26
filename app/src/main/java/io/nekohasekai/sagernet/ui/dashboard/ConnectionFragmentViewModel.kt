package io.nekohasekai.sagernet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class ConnectionFragmentState(
    val connection: Connection,
)

internal class ConnectionFragmentViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectionFragmentState(Connection()))
    val uiState = _uiState.asStateFlow()

    private var job: Job? = null

    fun initialize(conn: Connection?, queryConnections: () -> List<Connection>?) {
        conn?.let {
            _uiState.value = ConnectionFragmentState(it)
        }

        job = viewModelScope.launch {
            val interval = DataStore.speedInterval.takeIf { it > 0 }?.toLong() ?: 1000L
            while (isActive) {
                updateState(queryConnections)
                delay(interval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun updateState(queryConnections: (() -> List<Connection>?)) {
        val connections = try {
            queryConnections() ?: return
        } catch (_: Exception) {
            return
        }
        for (connection in connections) {
            if (connection.uuid == _uiState.value.connection.uuid) {
                _uiState.emit(_uiState.value.copy(connection = connection))
                break
            }
        }
    }
}