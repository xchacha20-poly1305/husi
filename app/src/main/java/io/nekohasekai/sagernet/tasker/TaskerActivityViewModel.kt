package io.nekohasekai.sagernet.tasker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class TaskerActivityUiState(
    val action: Int = 0,
    val profileID: Long = -1,
)

internal class TaskerActivityViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TaskerActivityUiState())
    val uiState = _uiState.asStateFlow()

    private lateinit var initialState: TaskerActivityUiState
    val isDirty = uiState.map { currentState ->
        initialState != currentState
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )

    fun loadFromSetting(action: Int, profileID: Long) {
        _uiState.update {
            it.copy(
                action = action,
                profileID = profileID,
            ).also {
                initialState = it
            }
        }
    }

    fun setAction(action: Int) = viewModelScope.launch {
        _uiState.update {
            it.copy(action = action)
        }
    }

    fun setProfileID(profileID: Long) = viewModelScope.launch {
        _uiState.update {
            it.copy(profileID = profileID)
        }
    }
}