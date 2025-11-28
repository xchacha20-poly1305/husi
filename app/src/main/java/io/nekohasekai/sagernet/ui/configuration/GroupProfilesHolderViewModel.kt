package io.nekohasekai.sagernet.ui.configuration

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ernestoyaquello.dragdropswipelazycolumn.OrderedItem
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.displayType
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.repository.repo
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
data class GroupProfilesHolderUiState(
    val profiles: List<ProfileItem> = emptyList(),
    val hiddenProfiles: Int = 0,
    val scrollIndex: Int? = null,
    val shouldRequestFocus: Boolean = false,
)

@Stable
data class ProfileItem(
    val profile: ProxyEntity,
    val isSelected: Boolean,
    val started: Boolean,
)

@Stable
class GroupProfilesHolderViewModel(
    val group: ProxyGroup,
    val preSelected: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupProfilesHolderUiState())
    val uiState = _uiState.asStateFlow()

    val alwaysShowAddress = DataStore.configurationStore.booleanFlow(Key.ALWAYS_SHOW_ADDRESS)
    val blurredAddress = DataStore.configurationStore.booleanFlow(Key.BLURRED_ADDRESS)
    val securityAdvisory = DataStore.configurationStore.booleanFlow(Key.SECURITY_ADVISORY)
    val selectedProxy = DataStore.configurationStore.longFlow(Key.PROFILE_ID)

    private var isFirstLoad = true
    private var loadJob: Job? = null
    private var deleteTimer: Job? = null
    private val hiddenProfileAccess = Mutex()
    private val hiddenProfileIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            SagerDatabase.proxyDao.getByGroup(group.id).collect { profiles ->
                val shouldScroll = isFirstLoad
                isFirstLoad = false
                reloadProfiles(profiles, shouldScroll)
            }
        }

        if (preSelected == null) {
            viewModelScope.launch {
                selectedProxy.collect {
                    reloadProfiles(null, false)
                }
            }
        }
    }

    fun submitReordered(changes: List<OrderedItem<ProfileItem>>) = runOnDefaultDispatcher {
        val toChange = changes.mapNotNull { orderedItem ->
            val profile = orderedItem.value.profile
            val newOrder = orderedItem.newIndex.toLong()
            if (profile.userOrder != newOrder) {
                profile.copy(userOrder = newOrder)
            } else {
                null
            }
        }
        if (toChange.isNotEmpty()) onIoDispatcher {
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
        val profiles = (raw ?: onIoDispatcher {
            SagerDatabase.proxyDao.getByGroup(group.id).first()
        })
            .filter {
                if (it.id in hiddenProfileIds) return@filter false
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

        _uiState.update { state ->
            state.copy(
                profiles = profiles,
                hiddenProfiles = hiddenProfileIds.size,
                scrollIndex = selectedIndex.takeIf { shouldScroll && selectedIndex >= 0 },
            )
        }
    }

    fun consumeScrollIndex() {
        _uiState.update { it.copy(scrollIndex = null) }
    }

    fun scrollToProxy(proxyId: Long, fallbackToTop: Boolean) {
        viewModelScope.launch {
            val profiles = _uiState.value.profiles
            val index = profiles.indexOfFirst { it.profile.id == proxyId }
            if (index >= 0) {
                _uiState.update { it.copy(scrollIndex = index) }
            } else if (fallbackToTop) {
                _uiState.update { it.copy(scrollIndex = 0) }
            }
        }
    }

    fun requestFocusIfNotHave() {
        _uiState.update { it.copy(shouldRequestFocus = true) }
    }

    fun consumeFocusRequest() {
        _uiState.update { it.copy(shouldRequestFocus = false) }
    }

    fun onProfileSelected(profileId: Long) {
        viewModelScope.launch {
            reloadProfiles(null, false)
        }
    }

    fun undoableRemove(id: Long) = viewModelScope.launch {
        hiddenProfileAccess.withLock {
            _uiState.update { state ->
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
            delay(5000)
            commit()
        }
    }

    fun undo() = viewModelScope.launch {
        deleteTimer?.cancel()
        deleteTimer = null
        hiddenProfileAccess.withLock {
            hiddenProfileIds.clear()
        }
        val profiles = onIoDispatcher { SagerDatabase.proxyDao.getByGroup(group.id).first() }
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
        onIoDispatcher {
            ProfileManager.deleteProfiles(group.id, toDelete)
        }
    }
}