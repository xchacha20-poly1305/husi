package io.nekohasekai.sagernet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal data class DashboardUiState(
    val isPausing: Boolean = false,
    val isConnectionUiVisible: Boolean = false
)

internal data class SortState(
    val sortMode: Int = DataStore.trafficSortMode,
    val isDescending: Boolean = DataStore.trafficDescending,
)

@OptIn(ExperimentalAtomicApi::class)
internal class DashboardFragmentViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    private val _sortState = MutableStateFlow(SortState())
    val sortState = _sortState.asStateFlow()

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
        _sortState.value = _sortState.value.copy(isDescending = isDescending)
    }

    fun setSortMode(mode: Int) {
        _sortState.value = _sortState.value.copy(sortMode = mode)
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
}
