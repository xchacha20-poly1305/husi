package io.nekohasekai.sagernet.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ernestoyaquello.dragdropswipelazycolumn.OrderedItem
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Stable
internal data class RouteFragmentUiState(
    val rules: List<RuleEntity> = emptyList(),
    val pendingDeleteCount: Int = 0,
)

@Stable
internal class RouteFragmentViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RouteFragmentUiState())
    val uiState = _uiState.asStateFlow()

    private var deleteTimer: Job? = null

    init {
        viewModelScope.launch {
            ProfileManager.getRules().distinctUntilChanged().collect(::reloadRules)
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
            )
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