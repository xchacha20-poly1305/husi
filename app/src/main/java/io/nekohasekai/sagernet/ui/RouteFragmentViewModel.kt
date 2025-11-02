package io.nekohasekai.sagernet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class RouteFragmentUiState(
    val rules: List<RuleEntity> = emptyList(),
)

internal class RouteFragmentViewModel : ViewModel(),
    UndoSnackbarManager.Interface<RuleEntity> {

    private val _uiState = MutableStateFlow(RouteFragmentUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ProfileManager.getRules().collect { rules ->
                _uiState.update {
                    it.copy(rules = rules)
                }
            }
        }
    }

    fun reset() = runOnIoDispatcher {
        SagerDatabase.rulesDao.reset()
        DataStore.rulesFirstCreate = false
    }


    fun toggleEnabled(rule: RuleEntity) = runOnIoDispatcher {
        rule.enabled = !rule.enabled
        SagerDatabase.rulesDao.updateRule(rule)
    }

    fun submitList(rules: List<RuleEntity>) = runOnDefaultDispatcher {
        val toUpdate = rules.mapIndexedNotNull { i, rule ->
            val newUserOrder = i.toLong()
            if (rule.userOrder != newUserOrder) {
                rule.userOrder = newUserOrder
                rule
            } else {
                null
            }
        }
        if (toUpdate.isNotEmpty()) onIoDispatcher {
            SagerDatabase.rulesDao.updateRules(toUpdate)
        }
    }

    suspend fun undoableRemove(index: Int) {
        val old = _uiState.value
        _uiState.emit(
            old.copy(
                rules = old.rules.toMutableList().apply {
                    removeAt(index)
                },
            )
        )
    }

    override fun undo(actions: List<Pair<Int, RuleEntity>>) {
        val rules = _uiState.value.rules.toMutableList()
        for ((index, rule) in actions) {
            rules.add(index, rule)
        }
        viewModelScope.launch {
            _uiState.emit(_uiState.value.copy(rules = rules))
        }
    }

    override fun commit(actions: List<Pair<Int, RuleEntity>>) {
        runOnDefaultDispatcher {
            val rules = actions.map { it.second }
            ProfileManager.deleteRules(rules)
        }
    }

}