package io.nekohasekai.sagernet.ui

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Stable
internal data class GroupUiState(
    val groups: List<GroupItemUiState> = emptyList(),
)

@Stable
data class GroupItemUiState(
    val group: ProxyGroup,
    val counts: Long,
    val isUpdating: Boolean = false,
    val updateProgress: GroupUpdateProgress? = null,
)

@Stable
data class GroupUpdateProgress(
    val progress: Float,
    val isIndeterminate: Boolean,
)

@Stable
internal class GroupFragmentViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            SagerDatabase.groupDao.allGroups().collect(::reloadGroups)
        }
    }

    private var deleteTimer: Job? = null
    private val hiddenGroupAccess = Mutex()
    private val hiddenGroup = mutableSetOf<Long>()

    private suspend fun reloadGroups(groups: List<ProxyGroup>?) = hiddenGroupAccess.withLock {
        _uiState.update { state ->
            state.copy(
                groups = (groups ?: onIoDispatcher {
                    SagerDatabase.groupDao.allGroups().first()
                }).mapNotNull {
                    val counts = onIoDispatcher {
                        SagerDatabase.proxyDao.countByGroup(it.id)
                    }
                    if (it.ungrouped && counts == 0L) {
                        null
                    } else if (it.id in hiddenGroup) {
                        null
                    } else {
                        buildItem(it, counts)
                    }
                },
            )
        }
    }

    private fun buildItem(group: ProxyGroup, counts: Long): GroupItemUiState {
        return GroupItemUiState(
            group = group,
            counts = counts,
            isUpdating = group.id in GroupUpdater.updating,
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
            reloadGroups(null)
        }
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
            SagerDatabase.groupDao.deleteByIds(toDelete)
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
        val links = SagerDatabase.proxyDao
            .getByGroup(group)
            .joinToString("\n") { it.toStdLink() }
        try {
            writeContent(links)
            showSnackbar(StringOrRes.Res(R.string.action_export_msg))
        } catch (e: Exception) {
            Logs.e(e)
            showSnackbar(StringOrRes.Direct(e.readableMessage))
        }
    }

}