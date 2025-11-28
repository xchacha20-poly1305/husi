package io.nekohasekai.sagernet.ui.dashboard

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.aidl.ProxySet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.Libcore

@Stable
data class DashboardState(
    // toolbar
    val searchQuery: String = "",
    val isPause: Boolean = false,
    val sortMode: Int = TrafficSortMode.START,
    val isDescending: Boolean = false,
    val queryOptions: Int = Libcore.ShowTrackerActively,

    val memory: Long = 0,
    val goroutines: Int = 0,
    val ipv4: String? = null,
    val ipv6: String? = null,
    val selectedClashMode: String = "",
    val clashModes: List<String> = emptyList(),
    val networkInterfaces: List<NetworkInterfaceInfo> = emptyList(),

    val connections: List<Connection> = emptyList(),

    val proxySets: List<ProxySet> = emptyList(),
){
    val showActivate = queryOptions and Libcore.ShowTrackerActively != 0
    val showClosed = queryOptions and Libcore.ShowTrackerClosed != 0
}

@Stable
data class NetworkInterfaceInfo(
    val name: String,
    val addresses: List<String>,
)

@Stable
class DashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            DataStore.configurationStore.intFlow(Key.TRAFFIC_SORT_MODE).collect {
                _uiState.update { state ->
                    state.copy(
                        sortMode = it,
                    )
                }
            }
        }
        viewModelScope.launch {
            DataStore.configurationStore.booleanFlow(Key.TRAFFIC_DESCENDING).collect {
                _uiState.update { state ->
                    state.copy(
                        isDescending = it,
                    )
                }
            }
        }
        viewModelScope.launch {
            DataStore.configurationStore.intFlow(Key.TRAFFIC_CONNECTION_QUERY).collect {
                _uiState.update { state ->
                    state.copy(
                        queryOptions = it,
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
            )
        }
    }

    fun togglePause() {
        _uiState.update { state ->
            state.copy(
                isPause = !state.isPause
            )
        }
    }

    fun setSortDescending(descending: Boolean) = runOnIoDispatcher {
        DataStore.trafficDescending = descending
    }

    fun setSortMode(mode: Int) = runOnIoDispatcher {
        DataStore.trafficSortMode = mode
    }

    fun setQueryActivate(queryActivate: Boolean) = runOnIoDispatcher {
        val old = uiState.value.queryOptions
        DataStore.trafficConnectionQuery = if (queryActivate) {
            old or Libcore.ShowTrackerActively
        } else {
            old and Libcore.ShowTrackerActively.inv()
        }
    }

    fun setQueryClosed(queryClosed: Boolean) = runOnIoDispatcher {
        val old = uiState.value.queryOptions
        DataStore.trafficConnectionQuery = if (queryClosed) {
            old or Libcore.ShowTrackerClosed
        } else {
            old and Libcore.ShowTrackerClosed.inv()
        }
    }

}