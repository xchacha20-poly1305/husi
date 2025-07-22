package io.nekohasekai.sagernet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class GroupUiState(
    val groups: List<GroupItemUiState> = emptyList(),
)

data class GroupItemUiState(
    val group: ProxyGroup,
    val counts: Long,
    val isUpdating: Boolean = false,
    val updateProgress: GroupUpdateProgress? = null,
)

data class GroupUpdateProgress(
    val progress: Int,
    val isIndeterminate: Boolean,
)

internal sealed interface GroupEvents {
    object FlushUndoManager : GroupEvents
}

internal class GroupFragmentViewModel : ViewModel(),
    GroupManager.Listener,
    UndoSnackbarManager.Interface<ProxyGroup> {

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<GroupEvents>()
    val uiEvent = _uiEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            loadGroups()
        }
    }

    private suspend fun loadGroups() {
        for (group in SagerDatabase.groupDao.allGroups()) {
            _uiState.update {
                it.copy(groups = it.groups + buildItem(group))
            }
        }
    }

    private fun buildItem(group: ProxyGroup): GroupItemUiState {
        val counts = SagerDatabase.proxyDao.countByGroup(group.id)
        return GroupItemUiState(
            group = group,
            counts = counts,
            isUpdating = group.id in GroupUpdater.updating,
            updateProgress = group.subscription?.let {
                GroupUpdateProgress(
                    progress = (it.bytesUsed.toDouble() / (it.bytesUsed + it.bytesRemaining).toDouble() * 100).toInt(),
                    isIndeterminate = true,
                )
            },
        )
    }

    fun fakeRemove(index: Int) = viewModelScope.launch {
        _uiState.update {
            it.copy(groups = it.groups.filterIndexed { i, _ -> i != index })
        }
    }

    fun move(from: Int, to: Int) {
        val groups = _uiState.value.groups.toMutableList()
        val movedItem = groups.removeAt(from)
        groups.add(to, movedItem)
        _uiState.update { it.copy(groups = groups) }
    }

    fun commitMove(groups: List<GroupItemUiState>) = runOnIoDispatcher {
        val groupsToUpdate = mutableListOf<ProxyGroup>()
        groups.forEachIndexed { i, item ->
            val index = i.toLong()
            if (item.group.userOrder != index) {
                item.group.userOrder = index
                groupsToUpdate.add(item.group)
            }
        }

        if (groupsToUpdate.isNotEmpty()) {
            groupsToUpdate.forEach { SagerDatabase.groupDao.updateGroup(it) }
        }
    }

    override suspend fun groupAdd(group: ProxyGroup) {
        _uiState.update { it.copy(it.groups + buildItem(group)) }
        if (group.type == GroupType.SUBSCRIPTION) {
            GroupUpdater.startUpdate(group, true)
        }
    }

    override suspend fun groupRemoved(groupId: Long) {
        _uiEvent.emit(GroupEvents.FlushUndoManager)
        _uiState.update {
            it.copy(groups = it.groups.filter { group ->
                group.group.id != groupId
            })
        }
    }

    override suspend fun groupUpdated(groupId: Long) {
        val group = SagerDatabase.groupDao.getById(groupId) ?: return
        groupUpdated(group)
    }

    override suspend fun groupUpdated(group: ProxyGroup) {
        _uiState.update {
            it.copy(groups = it.groups.mapX { item ->
                if (item.group.id == group.id) {
                    buildItem(group)
                } else {
                    item
                }
            })
        }
    }

    override fun undo(actions: List<Pair<Int, ProxyGroup>>) {
        _uiState.update {
            val groups = it.groups.toMutableList()
            for ((index, group) in actions) {
                groups.add(index, buildItem(group))
            }
            it.copy(groups = groups)
        }
    }

    override fun commit(actions: List<Pair<Int, ProxyGroup>>) {
        val groups = actions.mapX { it.second }
        runOnDefaultDispatcher {
            GroupManager.deleteGroup(groups)
        }
    }

    fun doUpdate(group: ProxyGroup) {
        GroupUpdater.startUpdate(group, true)
    }

    fun doUpdateAll() = runOnIoDispatcher {
        SagerDatabase.groupDao.allGroups()
            .filter { it.type == GroupType.SUBSCRIPTION }
            .forEach { group ->
                GroupUpdater.startUpdate(group, true)
            }
    }

    fun clearGroup(id: Long) = runOnIoDispatcher {
        GroupManager.clearGroup(id)
        // clearGroup will automatically notify changed.
        // _uiState.update { GroupUiState() }
    }

    private val _exportingGroup = MutableStateFlow<ProxyGroup?>(null)
    val exportingGroup = _exportingGroup.asStateFlow()

    fun prepareGroupForExport(group: ProxyGroup) {
        _exportingGroup.value = group
    }

    fun clearGroupExport() {
        _exportingGroup.value = null
    }
}