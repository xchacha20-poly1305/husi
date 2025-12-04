package io.nekohasekai.sagernet.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ernestoyaquello.dragdropswipelazycolumn.OrderedItem
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
data class GroupUiState(
    val groups: List<GroupItemUiState> = emptyList(),
    val hiddenGroups: Int = 0,
)

@Immutable
data class GroupItemUiState(
    val group: ProxyGroup,
    val counts: Long,
    val isUpdating: Boolean = false,
    val updateProgress: GroupUpdateProgress? = null,
)

@Immutable
data class GroupUpdateProgress(
    val progress: Float,
    val isIndeterminate: Boolean,
)

@Stable
class GroupScreenViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            SagerDatabase.groupDao.allGroups().collectLatest { groups ->
                if (groups.isEmpty()) {
                    _uiState.update { it.copy(groups = emptyList()) }
                    return@collectLatest
                }
                combine(
                    groups.map { group ->
                        SagerDatabase.proxyDao.countByGroup(group.id)
                            .map { count -> group to count }
                    }
                ) { it.toList() }.collect { groupsWithCounts ->
                    reloadGroups(groupsWithCounts)
                }
            }
        }
        viewModelScope.launch {
            GroupUpdater.updatingGroups.collect { updatingGroupIds ->
                _uiState.update { state ->
                    state.copy(
                        groups = state.groups.map { item ->
                            item.copy(isUpdating = item.group.id in updatingGroupIds)
                        },
                    )
                }
            }
        }
    }

    private var deleteTimer: Job? = null
    private val hiddenGroupAccess = Mutex()
    private val hiddenGroup = mutableSetOf<Long>()

    private suspend fun reloadGroups(groupsWithCounts: List<Pair<ProxyGroup, Long>>) = hiddenGroupAccess.withLock {
        _uiState.update { state ->
            state.copy(
                groups = groupsWithCounts.mapNotNull { (group, counts) ->
                    if (group.ungrouped && counts == 0L) {
                        null
                    } else if (group.id in hiddenGroup) {
                        null
                    } else {
                        buildItem(group, counts)
                    }
                },
                hiddenGroups = hiddenGroup.size,
            )
        }
    }

    private fun buildItem(group: ProxyGroup, counts: Long): GroupItemUiState {
        return GroupItemUiState(
            group = group,
            counts = counts,
            isUpdating = group.id in GroupUpdater.updatingGroups.value,
            updateProgress = group.subscription?.let {
                GroupUpdateProgress(
                    progress = (it.bytesUsed.toDouble() / (it.bytesUsed + it.bytesRemaining).toDouble()).toFloat(),
                    isIndeterminate = true,
                )
            },
        )
    }

    fun undoableRemove(id: Long) = viewModelScope.launch {
        hiddenGroupAccess.withLock {
            _uiState.update { state ->
                val groups = state.groups.toMutableList()
                val groupIndex = groups.indexOfFirst { it.group.id == id }
                if (groupIndex >= 0) {
                    groups.removeAt(groupIndex)
                    hiddenGroup.add(id)
                }
                state.copy(
                    groups = groups,
                    hiddenGroups = hiddenGroup.size,
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

    fun submitReorder(changes: List<OrderedItem<GroupItemUiState>>) = runOnDefaultDispatcher {
        val toUpdate = changes.mapNotNull { orderedItem ->
            val newUserOrder = orderedItem.newIndex.toLong()
            val group = orderedItem.value.group
            if (group.userOrder != newUserOrder) {
                group.copy(
                    userOrder = newUserOrder,
                )
            } else {
                null
            }
        }
        if (toUpdate.isNotEmpty()) onIoDispatcher {
            SagerDatabase.groupDao.updateGroups(toUpdate)
        }
    }

    fun undo() = viewModelScope.launch {
        deleteTimer?.cancel()
        deleteTimer = null
        hiddenGroupAccess.withLock {
            hiddenGroup.clear()
        }
        val groups = onIoDispatcher { SagerDatabase.groupDao.allGroups().first() }
        val groupsWithCounts = groups.map { group ->
            group to onIoDispatcher { SagerDatabase.proxyDao.countByGroup(group.id).first() }
        }
        reloadGroups(groupsWithCounts)
    }

    fun commit() = runOnDefaultDispatcher {
        deleteTimer?.cancel()
        deleteTimer = null
        val toDelete = hiddenGroupAccess.withLock {
            val toDelete = hiddenGroup.toList()
            hiddenGroup.clear()
            toDelete
        }
        onIoDispatcher {
            GroupManager.deleteGroup(toDelete)
        }
    }

    fun doUpdate(group: ProxyGroup) {
        GroupUpdater.startUpdate(group, true)
    }

    fun doUpdateAll() = runOnDefaultDispatcher {
        SagerDatabase.groupDao.allGroups().first()
            .filter { it.type == GroupType.SUBSCRIPTION }
            .forEach { group ->
                GroupUpdater.startUpdate(group, true)
            }
    }

    fun clearGroup(id: Long) = runOnIoDispatcher {
        GroupManager.clearGroup(id)
    }

    fun exportToFile(
        group: Long,
        writeContent: suspend (content: String) -> Unit,
        showSnackbar: (message: StringOrRes) -> Unit,
    ) = viewModelScope.launch {
        val links = onIoDispatcher {
            SagerDatabase.proxyDao
                .getByGroup(group)
                .first()
        }.joinToString("\n") { it.toStdLink() }
        try {
            writeContent(links)
            showSnackbar(StringOrRes.Res(R.string.action_export_msg))
        } catch (e: Exception) {
            Logs.e(e)
            showSnackbar(StringOrRes.Direct(e.readableMessage))
        }
    }

}