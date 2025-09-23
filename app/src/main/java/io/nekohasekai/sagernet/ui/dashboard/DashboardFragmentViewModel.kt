package io.nekohasekai.sagernet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.Libcore

internal data class ToolbarState(
    val searchQuery: String = "",
    val isPause: Boolean = false,
    val sortMode: Int = DataStore.trafficSortMode,
    val isDescending: Boolean = DataStore.trafficDescending,
    val queryOptions: Int = DataStore.trafficConnectionQuery,
) {
    val showActivate = queryOptions and Libcore.ShowTrackerActively != 0
    val showClosed = queryOptions and Libcore.ShowTrackerClosed != 0
}

internal class DashboardFragmentViewModel : ViewModel() {

    private val _toolbarState = MutableStateFlow(ToolbarState())
    val toolbarState = _toolbarState.asStateFlow()

    fun togglePausing() {
        _toolbarState.update {
            it.copy(isPause = !it.isPause)
        }
    }

    fun setSortDescending(isDescending: Boolean) = viewModelScope.launch {
        DataStore.trafficDescending = isDescending
        _toolbarState.update {
            it.copy(isDescending = isDescending)
        }
    }

    fun setSortMode(mode: Int) = viewModelScope.launch {
        DataStore.trafficSortMode = mode
        _toolbarState.update {
            it.copy(sortMode = mode)
        }
    }

    fun setSearchQuery(query: String) = viewModelScope.launch {
        _toolbarState.update {
            it.copy(searchQuery = query)
        }
    }

    var queryActivate
        get() = _toolbarState.value.queryOptions and Libcore.ShowTrackerActively != 0
        set(value) {
            viewModelScope.launch {
                _toolbarState.update {
                    val options = if (value) {
                        it.queryOptions or Libcore.ShowTrackerActively
                    } else {
                        it.queryOptions and Libcore.ShowTrackerActively.inv()
                    }
                    DataStore.trafficConnectionQuery = options
                    it.copy(queryOptions = options)
                }
            }
        }
    var queryClosed
        get() = toolbarState.value.queryOptions and Libcore.ShowTrackerClosed != 0
        set(value) {
            viewModelScope.launch {
                _toolbarState.update {
                    val options = if (value) {
                        it.queryOptions or Libcore.ShowTrackerClosed
                    } else {
                        it.queryOptions and Libcore.ShowTrackerClosed.inv()
                    }
                    DataStore.trafficConnectionQuery = options
                    it.copy(queryOptions = options)
                }
            }
        }
}
