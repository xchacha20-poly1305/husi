package io.nekohasekai.sagernet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import libcore.Libcore
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal data class DashboardUiState(
    val isPausing: Boolean = false,
    val isConnectionUiVisible: Boolean = false
)

internal data class ConnectionState(
    val sortMode: Int = DataStore.trafficSortMode,
    val isDescending: Boolean = DataStore.trafficDescending,
    val queryOptions: Int = DataStore.trafficConnectionQuery,
)

@OptIn(ExperimentalAtomicApi::class)
internal class DashboardFragmentViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState = _connectionState.asStateFlow()

    private val _searchQuery = MutableStateFlow<String?>(null)
    val searchQuery = _searchQuery.asStateFlow()

    private val pausing = AtomicBoolean(false)
    fun reversePausing() {
        val value = !pausing.load()
        pausing.store(value)

        viewModelScope.launch {
            _uiState.emit(_uiState.value.copy(isPausing = value))
        }
    }

    fun setSortDescending(isDescending: Boolean) {
        DataStore.trafficDescending = isDescending
        _connectionState.value = _connectionState.value.copy(isDescending = isDescending)
    }

    fun setSortMode(mode: Int) {
        DataStore.trafficSortMode = mode
        _connectionState.value = _connectionState.value.copy(sortMode = mode)
    }

    fun setSearchQuery(query: String?) {
        _searchQuery.value = query
    }

    fun onTabSelected(position: Int) {
        viewModelScope.launch {
            val newVisibility = position == DashboardFragment.POSITION_CONNECTIONS
            if (_uiState.value.isConnectionUiVisible != newVisibility) {
                _uiState.emit(_uiState.value.copy(isConnectionUiVisible = newVisibility))
            }
        }
    }

    var queryActivate
        get() = _connectionState.value.queryOptions and Libcore.ShowTrackerActively != 0
        set(value) {
            val old = connectionState.value
            viewModelScope.launch {
                val options = if (value) {
                    old.queryOptions or Libcore.ShowTrackerActively
                } else {
                    old.queryOptions and Libcore.ShowTrackerActively.inv()
                }
                DataStore.trafficConnectionQuery = options
                _connectionState.emit(
                    old.copy(
                        queryOptions = options,
                    )
                )
            }
        }
    var queryClosed
        get() = connectionState.value.queryOptions and Libcore.ShowTrackerClosed != 0
        set(value) {
            val old = connectionState.value
            viewModelScope.launch {
                val options = if (value) {
                    old.queryOptions or Libcore.ShowTrackerClosed
                } else {
                    old.queryOptions and Libcore.ShowTrackerClosed.inv()
                }
                DataStore.trafficConnectionQuery = options
                _connectionState.emit(
                    old.copy(
                        queryOptions = options,
                    )
                )
            }
        }
}
