package fr.husi.tasker

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class TaskerActivityUiState(
    val action: Int = 0,
    val profileID: Long = -1,
)

@Stable
internal class TaskerActivityViewModel : ViewModel() {

    val uiState: StateFlow<TaskerActivityUiState>
        field = MutableStateFlow(TaskerActivityUiState())

    private val initialState = MutableStateFlow<TaskerActivityUiState?>(null)
    val isDirty = combine(uiState, initialState) { currentState, initialState ->
        initialState?.let {
            it != currentState
        } ?: false
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )

    fun loadFromSetting(action: Int, profileID: Long) {
        uiState.update {
            it.copy(
                action = action,
                profileID = profileID,
            ).also {
                initialState.value = it
            }
        }
    }

    fun setAction(action: Int) = viewModelScope.launch {
        uiState.update {
            it.copy(action = action)
        }
    }

    fun setProfileID(profileID: Long) = viewModelScope.launch {
        uiState.update {
            it.copy(profileID = profileID)
        }
    }
}
