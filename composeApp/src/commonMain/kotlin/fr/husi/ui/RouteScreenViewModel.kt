package fr.husi.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ernestoyaquello.dragdropswipelazycolumn.OrderedItem
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.RuleEntity
import fr.husi.database.SagerDatabase
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.runOnIoDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
data class RouteFragmentUiState(
    val rules: List<RuleEntity> = emptyList(),
    val pendingDeleteCount: Int = 0,
)

@Stable
class RouteScreenViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RouteFragmentUiState())
    val uiState = _uiState.asStateFlow()

    private var deleteTimer: Job? = null

    init {
        viewModelScope.launch {
            ProfileManager.getRules().distinctUntilChanged().collectLatest(::reloadRules)
        }
    }

    private val hiddenRulesAccess = Mutex()
    private val hiddenRules = mutableSetOf<Long>()

    private suspend fun reloadRules(_rules: List<RuleEntity>?) {
        val rules = _rules ?: onIoDispatcher {
            ProfileManager.getRules().first()
        }
        hiddenRulesAccess.withLock {
            _uiState.update { state ->
                state.copy(
                    rules = rules.filterNot { hiddenRules.contains(it.id) },
                    pendingDeleteCount = hiddenRules.size,
                )
            }
        }
    }

    fun reset() = runOnIoDispatcher {
        SagerDatabase.rulesDao.reset()
        DataStore.rulesFirstCreate = false
        reloadRules(null)
    }

    fun toggleEnabled(rule: RuleEntity) = runOnIoDispatcher {
        ProfileManager.updateRule(
            rule.copy(
                enabled = !rule.enabled,
            ),
        )
    }

    fun submitReorder(changes: List<OrderedItem<RuleEntity>>) = runOnDefaultDispatcher {
        val toUpdate = changes.mapNotNull { orderedItem ->
            val newUserOrder = orderedItem.newIndex.toLong()
            if (orderedItem.value.userOrder != newUserOrder) {
                orderedItem.value.copy(
                    userOrder = newUserOrder,
                )
            } else {
                null
            }
        }
        if (toUpdate.isNotEmpty()) onIoDispatcher {
            SagerDatabase.rulesDao.updateRules(toUpdate)
        }
    }

    fun undoableRemove(id: Long) = viewModelScope.launch {
        hiddenRulesAccess.withLock {
            _uiState.update { state ->
                val rules = state.rules.toMutableList()
                val ruleIndex = rules.indexOfFirst { it.id == id }
                if (ruleIndex >= 0) {
                    val rule = rules.removeAt(ruleIndex)
                    hiddenRules.add(rule.id)
                }
                state.copy(
                    rules = rules,
                    pendingDeleteCount = hiddenRules.size,
                )
            }
        }
        startDeleteTimer()
    }

    private fun startDeleteTimer() {
        deleteTimer?.cancel()
        deleteTimer = viewModelScope.launch {
            delay(5000)
            commit()
        }
    }

    fun undo() = viewModelScope.launch {
        deleteTimer?.cancel()
        deleteTimer = null
        hiddenRulesAccess.withLock {
            hiddenRules.clear()
        }
        reloadRules(null)
    }

    fun commit() = runOnDefaultDispatcher {
        deleteTimer?.cancel()
        deleteTimer = null
        val toDelete = hiddenRulesAccess.withLock {
            val toDelete = hiddenRules.toList()
            hiddenRules.clear()
            toDelete
        }
        onIoDispatcher {
            ProfileManager.deleteRulesByIds(toDelete)
        }
    }

}