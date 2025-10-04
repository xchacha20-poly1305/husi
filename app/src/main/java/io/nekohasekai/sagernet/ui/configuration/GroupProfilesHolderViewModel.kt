package io.nekohasekai.sagernet.ui.configuration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.displayType
import io.nekohasekai.sagernet.fmt.Deduplication
import io.nekohasekai.sagernet.ktx.onDefaultDispatcher
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.removeFirstMatched
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class GroupProfilesHolderUiState(
    val profiles: List<ProfileItem> = emptyList(),
    val scrollIndex: Int? = null,
)

internal data class ProfileItem(
    val profile: ProxyEntity,
    val isSelected: Boolean,
    val started: Boolean,
)

internal sealed interface GroupProfilesHolderUiEvent {
    class AlertForDelete(val size: Int, val summary: String, val confirm: () -> Unit) :
        GroupProfilesHolderUiEvent
}

internal class GroupProfilesHolderViewModel : ViewModel(),
    ProfileManager.Listener, GroupManager.Listener,
    UndoSnackbarManager.Interface<ProfileItem> {

    init {
        ProfileManager.addListener(this)
        GroupManager.addListener(this)
    }

    override fun onCleared() {
        ProfileManager.removeListener(this)
        GroupManager.removeListener(this)
        super.onCleared()
    }

    private val _uiState = MutableStateFlow(GroupProfilesHolderUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<GroupProfilesHolderUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    val alwaysShowAddress: Boolean = DataStore.alwaysShowAddress
    val blurredAddress: Boolean = DataStore.blurredAddress
    val securityAdvisory: Boolean = DataStore.securityAdvisory

    var forSelect = false
    lateinit var group: ProxyGroup
    private var preSelected: Long? = null

    private var loadJob: Job? = null

    fun initialize(
        forSelect: Boolean,
        proxyGroup: ProxyGroup,
        preSelected: Long?,
    ) {
        val initialized = ::group.isInitialized

        this.forSelect = forSelect
        this.group = proxyGroup
        this.preSelected = preSelected

        if (!initialized) {
            loadJob = viewModelScope.launch {
                reloadProfiles(true)
            }
        }
    }

    fun updateOrder(order: Int) {
        if (group.order == order) return
        group.order = order
        runOnIoDispatcher {
            GroupManager.updateGroup(group)
        }
    }

    var query: String = ""
        set(value) {
            val lowercase = value.lowercase()
            if (lowercase != field) {
                field = lowercase
                loadJob?.cancel()
                loadJob = viewModelScope.launch {
                    reloadProfiles(false)
                }
            }
        }

    private suspend fun reloadProfiles(shouldScroll: Boolean) {
        val started = DataStore.serviceState.started
        val current = DataStore.currentProfile
        val selected = preSelected ?: DataStore.selectedProxy

        val comparator: Comparator<ProxyEntity> = when (group.order) {
            GroupOrder.BY_NAME -> compareBy { it.displayName() }
            GroupOrder.BY_DELAY -> compareBy {
                if (it.status == ProxyEntity.STATUS_AVAILABLE) {
                    it.ping
                } else {
                    Int.MAX_VALUE
                }
            }

            else -> compareBy { it.userOrder }
        }
        var selectedIndex = -1
        val profiles = SagerDatabase.proxyDao.getByGroup(group.id)
            .filter {
                val query = query
                if (query.isBlank()) {
                    true
                } else {
                    it.displayName().lowercase().contains(query)
                            || it.displayType(repo.context).lowercase().contains(query)
                            || it.displayAddress().lowercase().contains(query)
                }
            }
            .sortedWith(comparator)
            .mapIndexed { index, entity ->
                val isSelected = entity.id == selected
                if (isSelected) selectedIndex = index
                ProfileItem(
                    profile = entity,
                    isSelected = isSelected,
                    started = isSelected && started && entity.id == current,
                )
            }

        _uiState.emit(
            _uiState.value.copy(
                profiles = profiles,
                scrollIndex = selectedIndex.takeIf { shouldScroll && selectedIndex >= 0 },
            )
        )
    }


    fun onProfileSelect(new: Long) = viewModelScope.launch {
        _uiState.update { state ->
            val started = DataStore.serviceState.started

            state.copy(profiles = state.profiles.map {
                val isSelected = it.profile.id == new
                it.copy(
                    isSelected = isSelected,
                    started = started && isSelected,
                )
            }, scrollIndex = null)
        }
    }

    fun clearTrafficStatistics() = runOnIoDispatcher {
        val toClear = SagerDatabase.proxyDao.getByGroup(group.id).mapNotNull {
            if (it.tx != 0L || it.rx != 0L) {
                it.tx = 0L
                it.rx = 0L
                it
            } else {
                null
            }
        }
        if (toClear.isEmpty()) return@runOnIoDispatcher
        SagerDatabase.proxyDao.updateProxy(toClear)
        onDefaultDispatcher {
            reloadProfiles(false)
        }
    }

    fun clearResults() = runOnIoDispatcher {
        val toClear = SagerDatabase.proxyDao.getByGroup(group.id).mapNotNull {
            if (it.status != ProxyEntity.STATUS_INITIAL) {
                it.status = ProxyEntity.STATUS_INITIAL
                it.ping = 0
                it.error = null
                it
            } else {
                null
            }
        }
        if (toClear.isEmpty()) return@runOnIoDispatcher
        SagerDatabase.proxyDao.updateProxy(toClear)
        onDefaultDispatcher {
            reloadProfiles(false)
        }
    }

    fun deleteUnavailable() = viewModelScope.launch {
        val toClear = SagerDatabase.proxyDao.getByGroup(group.id).mapNotNull {
            when (it.status) {
                ProxyEntity.STATUS_INITIAL, ProxyEntity.STATUS_AVAILABLE -> null
                else -> it
            }
        }
        if (toClear.isEmpty()) return@launch

        fun confirmDelete() = runOnIoDispatcher {
            for (profile in toClear) {
                ProfileManager.deleteProfile(group.id, profile.id)
            }
        }
        _uiEvent.emit(
            GroupProfilesHolderUiEvent.AlertForDelete(
                toClear.size,
                nameSummary(toClear),
                ::confirmDelete,
            )
        )
    }

    fun removeDuplicate() = runOnDefaultDispatcher {
        val uniqueProxies = LinkedHashSet<Deduplication>()
        val toClear = SagerDatabase.proxyDao.getByGroup(group.id).mapNotNull {
            val bean = it.requireBean()
            val deduplication = Deduplication(bean, bean.javaClass.name)
            if (uniqueProxies.add(deduplication)) {
                null
            } else {
                it
            }
        }
        if (toClear.isEmpty()) return@runOnDefaultDispatcher

        fun confirmDelete() = runOnIoDispatcher {
            for (profile in toClear) {
                ProfileManager.deleteProfile(group.id, profile.id)
            }
        }

        _uiEvent.emit(
            GroupProfilesHolderUiEvent.AlertForDelete(
                toClear.size,
                nameSummary(toClear),
                ::confirmDelete,
            )
        )
    }

    private fun nameSummary(profiles: List<ProxyEntity>): String {
        return profiles.mapIndexedNotNull { index, entity ->
            when (index) {
                20 -> "......"
                in 1..19 -> entity.displayName()
                else -> null
            }
        }.joinToString("\n")
    }

    override fun undo(actions: List<Pair<Int, ProfileItem>>) {
        _uiState.update { state ->
            val profiles = state.profiles.toMutableList()
            for ((index, profile) in actions) {
                profiles.add(index, profile)
            }
            state.copy(profiles = profiles, scrollIndex = null)
        }
    }

    override fun commit(actions: List<Pair<Int, ProfileItem>>) {
        val profiles = actions.map { it.second }
        runOnIoDispatcher {
            for (profile in profiles) {
                ProfileManager.deleteProfile(profile.profile.groupId, profile.profile.id)
            }
        }
    }

    fun fakeRemove(index: Int) {
        _uiState.update { state ->
            val profiles = state.profiles.toMutableList()
            profiles.removeAt(index)
            state.copy(profiles = profiles, scrollIndex = null)
        }
    }

    fun move(from: Int, to: Int) {
        val profiles = _uiState.value.profiles.toMutableList()
        val moved = profiles.removeAt(from)
        profiles.add(to, moved)
        _uiState.update { state ->
            state.copy(profiles = profiles, scrollIndex = null)
        }
    }

    fun commitMove() = runOnDefaultDispatcher {
        val profilesToUpdate = mutableListOf<ProxyEntity>()
        for ((i, profile) in _uiState.value.profiles.withIndex()) {
            val index = i.toLong()
            if (profile.profile.userOrder != index) {
                profile.profile.userOrder = index
                profilesToUpdate.add(profile.profile)
            }
        }
        if (profilesToUpdate.isNotEmpty()) onIoDispatcher {
            SagerDatabase.proxyDao.updateProxy(profilesToUpdate)
        }
    }

    var exportConfig: String? = null
    var editingID: Long? = null

    override suspend fun onAdd(profile: ProxyEntity) {
        if (profile.groupId != group.id) return
        _uiState.update { state ->
            val selected = preSelected ?: DataStore.selectedProxy
            val isSelected = profile.id == selected
            state.copy(
                profiles = state.profiles + ProfileItem(
                    profile = profile,
                    isSelected = isSelected,
                    started = isSelected && DataStore.serviceState.started && profile.id == DataStore.currentProfile,
                ),
                scrollIndex = null,
            )
        }
    }

    override suspend fun onUpdated(data: TrafficData) {
        _uiState.update { state ->
            val profiles = state.profiles.toMutableList()
            val index = profiles.indexOfFirst { it.profile.id == data.id }
            if (index >= 0) {
                val target = profiles[index]
                profiles[index] = target.copy(
                    profile = target.profile.copy(
                        tx = data.tx,
                        rx = data.rx,
                    )
                )
                state.copy(profiles = profiles, scrollIndex = null)
            } else {
                state.copy(scrollIndex = null)
            }
        }
    }

    override suspend fun onUpdated(profile: ProxyEntity) {
        _uiState.update { state ->
            val profiles = state.profiles.toMutableList()
            val index = profiles.indexOfFirst { it.profile.id == profile.id }
            if (index >= 0) {
                val selectedId = DataStore.selectedProxy
                val currentId = DataStore.currentProfile
                val started = DataStore.serviceState.started
                val isSelected = profile.id == selectedId
                profiles[index] = profiles[index].copy(
                    profile = profile,
                    isSelected = isSelected,
                    started = isSelected && started && profile.id == currentId,
                )
            }
            state.copy(profiles = profiles, scrollIndex = null)
        }
    }

    override suspend fun onRemoved(groupId: Long, profileId: Long) {
        if (groupId != group.id) return
        _uiState.update { state ->
            val profiles = state.profiles.toMutableList()
            profiles.removeFirstMatched { it.profile.id == profileId }
            state.copy(profiles = profiles, scrollIndex = null)
        }
    }

    override suspend fun groupAdd(group: ProxyGroup) {}

    override suspend fun groupUpdated(group: ProxyGroup) {
        if (group.id != this.group.id) return
        this.group = group
        reloadProfiles(true)
    }

    override suspend fun groupRemoved(groupId: Long) {}

    override suspend fun groupUpdated(groupId: Long) {
        if (groupId != group.id) return
        this.group = SagerDatabase.groupDao.getById(groupId)!!
        reloadProfiles(true)
    }

}