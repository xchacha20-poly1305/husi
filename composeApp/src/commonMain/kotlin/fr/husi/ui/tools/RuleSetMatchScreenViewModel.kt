package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import fr.husi.libcore.Libcore
import fr.husi.resources.*
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class RuleSetMatchUiState(
    val keyword: String = "google.com",
    val matched: List<String> = emptyList(),
    val isDoing: Boolean = false,
)

@Immutable
internal sealed interface RuleSetMatchUiEvent {
    class Alert(val message: StringOrRes) : RuleSetMatchUiEvent
}

@Stable
internal class RuleSetMatchScreenViewModel : ViewModel() {
    val uiState: StateFlow<RuleSetMatchUiState>
        field = MutableStateFlow(RuleSetMatchUiState())

    val uiEvent: SharedFlow<RuleSetMatchUiEvent>
        field = MutableSharedFlow<RuleSetMatchUiEvent>()

    fun scan() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = uiState.value
            scan0(state.keyword)
        }
    }

    private val matched = mutableListOf<String>() // reuse

    private suspend fun scan0(keyword: String) {
        uiState.update { it.copy(isDoing = true) }
        matched.clear()
        try {
            Libcore.scanRuleSet(keyword) {
                matched.add(it)
                uiState.update { state ->
                    state.copy(matched = matched)
                }
            }
            if (matched.isEmpty()) {
                uiEvent.emit(RuleSetMatchUiEvent.Alert(StringOrRes.Res(Res.string.not_found)))
            }
        } catch (e: Exception) {
            Logs.e(e)
            uiEvent.emit(RuleSetMatchUiEvent.Alert(StringOrRes.Direct(e.readableMessage)))
        } finally {
            uiState.update { it.copy(isDoing = false) }
        }
    }

    fun setKeyword(keyword: String) {
        uiState.update { it.copy(keyword = keyword) }
    }
}