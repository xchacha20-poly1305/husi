package fr.husi.ui.configuration

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ernestoyaquello.dragdropswipelazycolumn.OrderedItem
import fr.husi.GroupOrder
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.database.displayType
import fr.husi.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class GroupProfilesHolderUiState(
    val profiles: List<ProfileItem> = emptyList(),
    val hiddenProfiles: Int = 0,
    val scrollIndex: Int? = null,
    /**
     * Whether the displayed positions are the persisted user order, which is what drag-and-drop
     * reordering writes back. A search query or a group order other than [GroupOrder.ORIGIN]
     * makes them unrelated to it, so dragging has to stay disabled there.
     */
    val canReorder: Boolean = true,
)

@Immutable
data class ProfileItem(
    val profile: ProxyEntity,
    val isSelected: Boolean,
    val started: Boolean,
)

private data class PendingScrollToProxy(
    val proxyId: Long,
    val fallbackToTop: Boolean,
)

@Stable
class GroupProfilesHolderViewModel(
    initialGroup: ProxyGroup,
    val preSelected: Long?,
) : ViewModel() {

    var group: ProxyGroup = initialGroup
        private set

    val uiState: StateFlow<GroupProfilesHolderUiState>
        field = MutableStateFlow(GroupProfilesHolderUiState())

    val alwaysShowAddress = DataStore.alwaysShowAddress.flow()
    val blurredAddress = DataStore.blurredAddress.flow()
    val trafficStatistics = DataStore.profileTrafficStatistics.flow()
    val securityAdvisory = DataStore.securityAdvisory.flow()
    val selectedProxy = DataStore.selectedProxy.flow()

    private var isFirstLoad = true
    private var observeJob: Job? = null
    private var loadJob: Job? = null
    private var deleteTimer: Job? = null
    private var hasLoadedProfiles = false
    private var pendingScrollToProxy: PendingScrollToProxy? = null
    private val hiddenProfileAccess = Mutex()
    private val hiddenProfileIds = mutableSetOf<Long>()

    fun startObserving() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            coroutineScope {
                launch {
                    SagerDatabase.proxyDao.getByGroup(group.id).collect { profiles ->
                        val shouldScroll = isFirstLoad
                        isFirstLoad = false
                        reloadProfiles(profiles, shouldScroll)
                    }
                }

                if (preSelected == null) {
                    launch {
                        selectedProxy.collect {
                            reloadProfiles(null, false)
                        }
                    }
                }

                launch {
                    SagerDatabase.groupDao.getById(group.id).collectLatest { updated ->
                        if (updated != null && updated != group) {
                            group = updated
                            reloadProfiles(null, false)
                        }
                    }
                }
            }
        }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    fun submitReordered(changes: List<OrderedItem<ProfileItem>>) = runOnDefaultDispatcher {
        val state = uiState.value
        if (changes.isEmpty() || !state.canReorder) return@runOnDefaultDispatcher

        val reordered = state.profiles.toMutableList()
        for (change in changes) {
            if (change.newIndex !in reordered.indices) {
                return@runOnDefaultDispatcher
            }
            reordered[change.newIndex] = change.value
        }

        val toChange = reordered.mapIndexedNotNull { index, item ->
            val newOrder = (index + 1).toLong()
            val profile = item.profile
            if (profile.userOrder != newOrder) {
                profile.copy(userOrder = newOrder)
            } else {
                null
            }
        }
        if (toChange.isNotEmpty()) withContext(Dispatchers.IO) {
            ProfileManager.updateProfile(toChange)
        }
    }

    var query: String = ""
        set(value) {
            val lowercase = value.lowercase()
            if (lowercase != field) {
                field = lowercase
                loadJob?.cancel()
                loadJob = viewModelScope.launch {
                    reloadProfiles(null, false)
                }
            }
        }

    private suspend fun reloadProfiles(
        raw: List<ProxyEntity>?,
        shouldScroll: Boolean,
    ) = hiddenProfileAccess.withLock {
        val started = DataStore.serviceState.started
        val current = DataStore.currentProfile.get()
        val selected = preSelected ?: DataStore.selectedProxy.get()

        val comparator: Comparator<ProxyEntity> = when (group.order) {
            GroupOrder.BY_NAME -> compareBy { it.displayName() }
            GroupOrder.BY_DELAY -> compareBy<ProxyEntity> {
                when {
                    it.status == ProxyEntity.STATUS_AVAILABLE -> 0
                    !it.error.isNullOrBlank() -> 1
                    else -> 2
                }
            }.thenBy {
                if (it.status == ProxyEntity.STATUS_AVAILABLE) {
                    it.ping
                } else {
                    0
                }
            }

            else -> compareBy<ProxyEntity> { it.userOrder }.thenBy { it.id }
        }
        var selectedIndex = -1
        val profiles = (raw ?: withContext(Dispatchers.IO) {
            SagerDatabase.proxyDao.getByGroup(group.id).first()
        })
            .filter {
                if (it.id in hiddenProfileIds) return@filter false
                val query = query
                if (query.isBlank()) {
                    true
                } else {
                    it.displayName().lowercase().contains(query)
                            || it.displayType().lowercase().contains(query)
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
        hasLoadedProfiles = true
        var scrollIndex = selectedIndex.takeIf { shouldScroll && selectedIndex >= 0 }
        pendingScrollToProxy?.let { pending ->
            val pendingIndex = profiles.indexOfFirst { it.profile.id == pending.proxyId }
            scrollIndex = when {
                pendingIndex >= 0 -> pendingIndex
                pending.fallbackToTop && profiles.isNotEmpty() -> 0
                else -> scrollIndex
            }
            pendingScrollToProxy = null
        }

        uiState.update { state ->
            state.copy(
                profiles = profiles,
                hiddenProfiles = hiddenProfileIds.size,
                scrollIndex = scrollIndex,
                canReorder = query.isBlank() && group.order == GroupOrder.ORIGIN,
            )
        }
    }

    fun consumeScrollIndex() {
        uiState.update { it.copy(scrollIndex = null) }
    }

    fun scrollToProxy(proxyId: Long, fallbackToTop: Boolean) {
        viewModelScope.launch {
            val profiles = uiState.value.profiles
            val index = profiles.indexOfFirst { it.profile.id == proxyId }
            if (index >= 0) {
                uiState.update { it.copy(scrollIndex = index) }
            } else if (fallbackToTop) {
                if (profiles.isNotEmpty()) {
                    uiState.update { it.copy(scrollIndex = 0) }
                } else if (!hasLoadedProfiles) {
                    pendingScrollToProxy = PendingScrollToProxy(proxyId, fallbackToTop)
                }
            }
        }
    }

    fun profileToSelect(delta: Int): Long? {
        val profiles = uiState.value.profiles
        if (profiles.isEmpty()) return null

        val currentIndex = profiles.indexOfFirst { it.isSelected }
        val targetIndex = if (currentIndex < 0) {
            0
        } else {
            (currentIndex + delta).coerceIn(0, profiles.lastIndex)
        }
        return profiles.getOrNull(targetIndex)?.profile?.id
            ?.takeIf { targetIndex != currentIndex }
    }

    fun onProfileSelected(profileId: Long) {
        viewModelScope.launch {
            reloadProfiles(null, false)
        }
    }

    fun undoableRemove(id: Long) = viewModelScope.launch {
        hiddenProfileAccess.withLock {
            uiState.update { state ->
                val profiles = state.profiles.toMutableList()
                val index = profiles.indexOfFirst { it.profile.id == id }
                if (index >= 0) {
                    profiles.removeAt(index)
                    hiddenProfileIds.add(id)
                }
                state.copy(
                    profiles = profiles,
                    hiddenProfiles = hiddenProfileIds.size,
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
        hiddenProfileAccess.withLock {
            hiddenProfileIds.clear()
        }
        val profiles = withContext(Dispatchers.IO) { SagerDatabase.proxyDao.getByGroup(group.id).first() }
        reloadProfiles(profiles, false)
    }

    fun commit() = runOnDefaultDispatcher {
        deleteTimer?.cancel()
        deleteTimer = null
        val toDelete = hiddenProfileAccess.withLock {
            val toDelete = hiddenProfileIds.toList()
            hiddenProfileIds.clear()
            toDelete
        }
        withContext(Dispatchers.IO) {
            ProfileManager.deleteProfiles(group.id, toDelete)
        }
    }
}
