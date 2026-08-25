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
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.runOnIoDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class RouteFragmentUiState(
    val rules: List<RuleEntity> = emptyList(),
    val pendingDeleteCount: Int = 0,
)

@Stable
class RouteScreenViewModel : ViewModel() {

    val uiState: StateFlow<RouteFragmentUiState>
        field = MutableStateFlow(RouteFragmentUiState())

    private var deleteTimer: Job? = null

    init {
        viewModelScope.launch {
            ProfileManager.getRules().distinctUntilChanged().collectLatest(::reloadRules)
        }
    }

    private val hiddenRulesAccess = Mutex()
    private val hiddenRules = mutableSetOf<Long>()

    private suspend fun reloadRules(_rules: List<RuleEntity>?) {
        val rules = _rules ?: withContext(Dispatchers.IO) {
            ProfileManager.getRules().first()
        }
        hiddenRulesAccess.withLock {
            uiState.update { state ->
                state.copy(
                    rules = rules.filterNot { hiddenRules.contains(it.id) },
                    pendingDeleteCount = hiddenRules.size,
                )
            }
        }
    }

    fun reset() = runOnIoDispatcher {
        SagerDatabase.rulesDao.reset()
        DataStore.rulesFirstCreate.set(false)
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
        if (changes.isEmpty()) return@runOnDefaultDispatcher

        val reordered = uiState.value.rules.toMutableList()
        for (change in changes) {
            if (change.newIndex !in reordered.indices) {
                return@runOnDefaultDispatcher
            }
            reordered[change.newIndex] = change.value
        }

        val toUpdate = reordered.mapIndexedNotNull { index, rule ->
            val newUserOrder = (index + 1).toLong()
            if (rule.userOrder != newUserOrder) {
                rule.copy(userOrder = newUserOrder)
            } else {
                null
            }
        }
        if (toUpdate.isNotEmpty()) withContext(Dispatchers.IO) {
            SagerDatabase.rulesDao.updateRules(toUpdate)
        }
    }

    fun undoableRemove(id: Long) = viewModelScope.launch {
        hiddenRulesAccess.withLock {
            uiState.update { state ->
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
            delay(5000.milliseconds)
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
        withContext(Dispatchers.IO) {
            ProfileManager.deleteRulesByIds(toDelete)
        }
    }

}