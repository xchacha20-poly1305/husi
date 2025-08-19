package io.nekohasekai.sagernet.tasker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

internal sealed interface TaskerActivityUiEvent {
    object EnableBackPressCallback : TaskerActivityUiEvent
    class SetAction(val action: Int) : TaskerActivityUiEvent
    class SetProfileID(val id: Long): TaskerActivityUiEvent
}

internal class TaskerActivityViewModel : ViewModel(), OnPreferenceDataStoreChangeListener {
    private val _uiEvent = MutableSharedFlow<TaskerActivityUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    var dirty = false

    fun onSelectProfile(id: Long) = runOnDefaultDispatcher {
        DataStore.taskerProfileId = id
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        dirty = true

        viewModelScope.launch {
            _uiEvent.emit(TaskerActivityUiEvent.EnableBackPressCallback)

            when (key) {
                Key.TASKER_ACTION -> {
                    _uiEvent.emit(TaskerActivityUiEvent.SetAction(DataStore.taskerAction))
                }

                Key.TASKER_PROFILE -> if (DataStore.taskerProfile == 0) {
                    DataStore.taskerProfileId = -1L
                }

                Key.TASKER_PROFILE_ID -> {
                    _uiEvent.emit(TaskerActivityUiEvent.SetProfileID(DataStore.taskerProfileId))
                }
            }
        }
    }
}