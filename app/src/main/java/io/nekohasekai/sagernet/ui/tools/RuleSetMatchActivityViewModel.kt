package io.nekohasekai.sagernet.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.Libcore

internal sealed interface RuleSetMatchUiState {
    object Idle : RuleSetMatchUiState
    class Doing(val matched: List<String> = emptyList()) : RuleSetMatchUiState
    class Done(val matched: List<String>, val exception: Exception? = null) : RuleSetMatchUiState
}

internal class RuleSetMatchActivityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<RuleSetMatchUiState>(RuleSetMatchUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun scan(keyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            scan0(keyword)
        }
    }

    private suspend fun scan0(keyword: String) {
        _uiState.update { RuleSetMatchUiState.Doing() }
        val matched = mutableListOf<String>()
        try {
            Libcore.scanRuleSet(keyword) {
                matched.add(it)
                _uiState.update { RuleSetMatchUiState.Doing(matched.toList()) }
            }
            _uiState.update { RuleSetMatchUiState.Done(matched) }
        } catch (e: Exception) {
            _uiState.update { RuleSetMatchUiState.Done(matched,e) }
        }
    }
}