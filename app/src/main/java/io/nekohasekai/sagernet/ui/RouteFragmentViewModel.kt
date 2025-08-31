package io.nekohasekai.sagernet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class RouteFragmentUiState(
    val rules: List<RuleEntity> = emptyList(),
)

internal sealed interface RouteFragmentUiEvent {
    object NeedReload : RouteFragmentUiEvent
}

internal class RouteFragmentViewModel : ViewModel(),
    UndoSnackbarManager.Interface<RuleEntity> {

    private val _uiState = MutableStateFlow(RouteFragmentUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<RouteFragmentUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            reload()
        }
    }

    suspend fun reset() {
        SagerDatabase.rulesDao.reset()
        DataStore.rulesFirstCreate = false
        reload()
    }

    private suspend fun reload() {
        _uiState.emit(
            RouteFragmentUiState(
                rules = ProfileManager.getRules(),
            )
        )
    }

    suspend fun onAdd(rule: RuleEntity) {
        val old = _uiState.value
        _uiState.emit(
            old.copy(
                rules = old.rules + rule,
            )
        )
        _uiEvent.emit(RouteFragmentUiEvent.NeedReload)
    }

    suspend fun onUpdated(rule: RuleEntity) {
        val old = _uiState.value
        val rules = old.rules.toMutableList()
        val changedIndex = rules.indexOfFirst { it.id == rule.id }.takeIf { it >= 0 } ?: return
        rules[changedIndex] = rule
        _uiState.emit(
            old.copy(
                rules = rules,
            )
        )
        _uiEvent.emit(RouteFragmentUiEvent.NeedReload)
    }

    suspend fun onRemoved(id: Long) {
        val old = _uiState.value
        val rules = old.rules.toMutableList()
        rules.removeIf { it.id == id }
        _uiState.emit(
            old.copy(
                rules = rules,
            )
        )
        _uiEvent.emit(RouteFragmentUiEvent.NeedReload)
    }

    suspend fun onRulesCleared() {
        val old = _uiState.value
        _uiState.emit(old.copy(rules = emptyList()))
        _uiEvent.emit(RouteFragmentUiEvent.NeedReload)
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

    suspend fun fakeMove(from: Int, to: Int) {
        val old = _uiState.value
        val rules = old.rules.toMutableList()
        val moved = rules.removeAt(from)
        rules.add(to, moved)
        _uiState.emit(old.copy(rules = rules))
    }

    fun commitMove(movedRules: List<RuleEntity>) = runOnDefaultDispatcher {
        val shouldUpdates = mutableListOf<RuleEntity>()
        for ((i, rule) in movedRules.withIndex()) {
            val index = i.toLong()
            if (rule.userOrder != index) {
                rule.userOrder = index
                shouldUpdates.add(rule)
            }
        }

        if (shouldUpdates.isNotEmpty()) {
            SagerDatabase.rulesDao.updateRules(shouldUpdates)
            _uiEvent.emit(RouteFragmentUiEvent.NeedReload)
        }
    }

    fun updateRule(rule: RuleEntity) = runOnDefaultDispatcher {
        SagerDatabase.rulesDao.updateRule(rule)
        _uiEvent.emit(RouteFragmentUiEvent.NeedReload)
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
        val rules = actions.map { it.second }
        runOnDefaultDispatcher {
            ProfileManager.deleteRules(rules)
        }
    }

}