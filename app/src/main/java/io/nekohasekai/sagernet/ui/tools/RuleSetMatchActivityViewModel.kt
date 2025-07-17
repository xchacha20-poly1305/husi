package io.nekohasekai.sagernet.ui.tools

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import libcore.Libcore

internal sealed class RuleSetMatchUiState {
    object Idle : RuleSetMatchUiState()
    class Doing(val matched: List<String> = emptyList()) : RuleSetMatchUiState()
    class Done(val exception: Exception? = null) : RuleSetMatchUiState()
}

internal class RuleSetMatchActivityViewModel : ViewModel() {
    private val _uiState: MutableLiveData<RuleSetMatchUiState> =
        MutableLiveData(RuleSetMatchUiState.Idle)
    val uiState: LiveData<RuleSetMatchUiState> = _uiState

    fun scan(keyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            scan0(keyword)
        }
    }

    private suspend fun scan0(keyword: String) {
        _uiState.postValue(RuleSetMatchUiState.Doing())
        try {
            val all = mutableListOf<String>()
            Libcore.scanRuleSet(keyword) {
                all.add(it)
                _uiState.postValue(RuleSetMatchUiState.Doing(all.toList()))
            }
            _uiState.postValue(RuleSetMatchUiState.Done())
        } catch (e: Exception) {
            _uiState.postValue(RuleSetMatchUiState.Done(e))
        }
    }
}