package fr.husi.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ernestoyaquello.dragdropswipelazycolumn.OrderedItem
import fr.husi.database.GroupManager
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.group.GroupUpdater
import fr.husi.ktx.Logs
import fr.husi.ktx.readableMessage
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.resources.Res
import fr.husi.resources.action_export_msg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

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

    val uiState: StateFlow<GroupUiState>
        field = MutableStateFlow(GroupUiState())

    init {
        viewModelScope.launch {
            SagerDatabase.groupDao.allGroups().collectLatest { groups ->
                if (groups.isEmpty()) {
                    uiState.update { it.copy(groups = emptyList()) }
                    return@collectLatest
                }
                combine(
                    groups.map { group ->
                        SagerDatabase.proxyDao.countByGroup(group.id)
                            .map { count -> group to count }
                    },
                ) { it.toList() }.collect { groupsWithCounts ->
                    reloadGroups(groupsWithCounts)
                }
            }
        }
        viewModelScope.launch {
            GroupUpdater.updatingGroups.collect { updatingGroupIds ->
                uiState.update { state ->
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

    private suspend fun reloadGroups(groupsWithCounts: List<Pair<ProxyGroup, Long>>) =
        hiddenGroupAccess.withLock {
            uiState.update { state ->
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
            uiState.update { state ->
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
            delay(5000.milliseconds)
            commit()
        }
    }

    fun submitReorder(changes: List<OrderedItem<GroupItemUiState>>) = runOnDefaultDispatcher {
        if (changes.isEmpty()) return@runOnDefaultDispatcher

        val reordered = uiState.value.groups.toMutableList()
        for (change in changes) {
            if (change.newIndex !in reordered.indices) {
                return@runOnDefaultDispatcher
            }
            reordered[change.newIndex] = change.value
        }

        val toUpdate = reordered.mapIndexedNotNull { index, groupState ->
            val newUserOrder = (index + 1).toLong()
            val group = groupState.group
            if (group.userOrder != newUserOrder) {
                group.copy(userOrder = newUserOrder)
            } else {
                null
            }
        }
        if (toUpdate.isNotEmpty()) withContext(Dispatchers.IO) {
            SagerDatabase.groupDao.updateGroups(toUpdate)
        }
    }

    fun undo() = viewModelScope.launch {
        deleteTimer?.cancel()
        deleteTimer = null
        hiddenGroupAccess.withLock {
            hiddenGroup.clear()
        }
        val groupsWithCounts = withContext(Dispatchers.IO) {
            val groups = SagerDatabase.groupDao.allGroups().first()
            groups.map { group ->
                group to SagerDatabase.proxyDao.countByGroup(group.id).first()
            }
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
        withContext(Dispatchers.IO) {
            GroupManager.deleteGroup(toDelete)
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
        val links = withContext(Dispatchers.IO) {
            SagerDatabase.proxyDao
                .getByGroup(group)
                .first()
        }.joinToString("\n") { it.toStdLink() }
        try {
            writeContent(links)
            showSnackbar(StringOrRes.Res(Res.string.action_export_msg))
        } catch (e: Exception) {
            Logs.e(e)
            showSnackbar(StringOrRes.Direct(e.readableMessage))
        }
    }

}
