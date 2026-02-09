package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.R
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import fr.husi.libcore.Libcore
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
internal class RuleSetMatchActivityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(RuleSetMatchUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<RuleSetMatchUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun scan() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            scan0(state.keyword)
        }
    }

    private val matched = mutableListOf<String>() // reuse

    private suspend fun scan0(keyword: String) {
        _uiState.update { it.copy(isDoing = true) }
        matched.clear()
        try {
            Libcore.scanRuleSet(keyword) {
                matched.add(it)
                _uiState.update { state ->
                    state.copy(matched = matched)
                }
            }
            if (matched.isEmpty()) {
                _uiEvent.emit(RuleSetMatchUiEvent.Alert(StringOrRes.Res(R.string.not_found)))
            }
        } catch (e: Exception) {
            Logs.e(e)
            _uiEvent.emit(RuleSetMatchUiEvent.Alert(StringOrRes.Direct(e.readableMessage)))
        } finally {
            _uiState.update { it.copy(isDoing = false) }
        }
    }

    fun setKeyword(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
    }
}