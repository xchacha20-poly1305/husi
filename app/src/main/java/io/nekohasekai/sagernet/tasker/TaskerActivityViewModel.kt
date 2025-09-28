package io.nekohasekai.sagernet.tasker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class TaskerActivityUiState(
    val action: Int = 0,
    val profileID: Long = -1,
)

internal class TaskerActivityViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TaskerActivityUiState())
    val uiState = _uiState.asStateFlow()

    private val _dirty = MutableStateFlow(false)
    val dirty = _dirty.asStateFlow()

    fun loadFromSetting(action: Int, profileID: Long) {
        _uiState.update {
            it.copy(
                action = action,
                profileID = profileID,
            )
        }
    }

    fun setAction(action: Int) = viewModelScope.launch {
        _uiState.update {
            it.copy(action = action)
        }
        _dirty.value = true
    }

    fun setProfileID(profileID: Long) = viewModelScope.launch {
        _uiState.update {
            it.copy(profileID = profileID)
        }
        _dirty.value = true
    }
}