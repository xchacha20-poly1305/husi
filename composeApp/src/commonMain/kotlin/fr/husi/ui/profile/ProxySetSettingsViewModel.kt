package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.ernestoyaquello.dragdropswipelazycolumn.OrderedItem
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.ktx.applyDefaultValues
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.onDefaultDispatcher
import fr.husi.resources.Res
import fr.husi.resources.circular_reference
import fr.husi.resources.circular_reference_sum
import fr.husi.resources.duplicate_name
import fr.husi.resources.error_title
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class ProxySetUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val management: Int = ProxySetBean.MANAGEMENT_SELECTOR,
    val interruptExistConnections: Boolean = false,

    val testURL: String = "",
    val testInterval: String = "",
    val testIdleTimeout: String = "",
    val testTolerance: Int = 50,

    val collectType: Int = ProxySetBean.TYPE_LIST,
    val groupID: Long = -1L,
    val filterNotRegex: String = "",

    val profiles: List<ProxyEntity> = emptyList(),
    val groups: LinkedHashMap<Long, ProxyGroup> = LinkedHashMap(),
) : ProfileEditorUiState

@Stable
internal class ProxySetSettingsViewModel : ProfileEditorViewModel<ProxySetBean>() {

    override val uiState: StateFlow<ProxySetUiState>
        field = MutableStateFlow(ProxySetUiState())

    override fun createBean() = ProxySetBean().applyDefaultValues()

    override suspend fun ProxySetBean.writeToUiState() {
        uiState.update {
            it.copy(
                name = name,
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
                management = management,
                interruptExistConnections = interruptExistConnections,
                testURL = testURL,
                testInterval = testInterval,
                testIdleTimeout = testIdleTimeout,
                testTolerance = testTolerance,
                collectType = type,
                groupID = groupId,
                filterNotRegex = groupFilterNotRegex,
            )
        }
        load(proxies)
    }

    override fun ProxySetBean.loadFromUiState() {
        val state = uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = state.name
        management = state.management
        interruptExistConnections = state.interruptExistConnections
        testURL = state.testURL
        testInterval = state.testInterval
        testIdleTimeout = state.testIdleTimeout
        testTolerance = state.testTolerance
        type = state.collectType
        groupId = state.groupID
        groupFilterNotRegex = state.filterNotRegex
        proxies = state.profiles.map { it.id }
    }

    private suspend fun load(ids: List<Long>) {
        val groups = SagerDatabase.groupDao.allGroups().first()
        val groupMap = LinkedHashMap<Long, ProxyGroup>(groups.size)
        groups.associateByTo(groupMap) { it.id }
        uiState.update {
            it.copy(groups = groupMap)
        }
        val proxyList = ArrayList<ProxyEntity>(ids.size)
        val profiles = ProfileManager.getProfiles(ids).associateBy { it.id }
        onDefaultDispatcher {
            for (id in ids) {
                proxyList.add(profiles[id] ?: continue)
            }
        }
        uiState.update {
            it.copy(profiles = proxyList)
        }
    }

    fun submitReorder(changes: List<OrderedItem<ProxyEntity>>) {
        invalidateProfileSelection()
        val currentProfiles = uiState.value.profiles
        val changesMap = changes.associate { it.value.id to it.newIndex }

        val reordered = currentProfiles.sortedBy { profile ->
            changesMap[profile.id] ?: currentProfiles.indexOf(profile)
        }

        uiState.update {
            it.copy(profiles = reordered)
        }
    }

    fun remove(index: Int) {
        invalidateProfileSelection()
        val profiles = uiState.value.profiles.toMutableList()
        profiles.removeAt(index)
        uiState.update {
            it.copy(profiles = profiles)
        }
    }

    /** The profile index that is being replacing */
    var replacing = -1

    private var profileSelectionJob: Job? = null
    private var profileMutationVersion = 0L

    private fun invalidateProfileSelection() {
        profileMutationVersion++
        profileSelectionJob?.cancel()
        profileSelectionJob = null
    }

    fun onSelectProfile(id: Long) {
        val replacingIndex = replacing
        replacing = -1
        val version = ++profileMutationVersion
        profileSelectionJob?.cancel()
        profileSelectionJob = viewModelScope.launch {
            val profile = ProfileManager.getProfile(id)!!
            if (version != profileMutationVersion) return@launch
            val profiles = uiState.value.profiles.toMutableList()
            if (replacingIndex >= profiles.size) return@launch
            val otherProfiles = profiles.filterIndexed { index, _ -> index != replacingIndex }
            if (otherProfiles.any { it.id == profile.id }) {
                emitAlert(
                    title = StringOrRes.Res(Res.string.duplicate_name),
                    message = StringOrRes.Direct(profile.displayName()),
                )
                return@launch
            }
            if (!profile.canAdd(otherProfiles)) {
                if (version != profileMutationVersion) return@launch
                emitAlert(
                    title = StringOrRes.Res(Res.string.circular_reference),
                    message = StringOrRes.Res(Res.string.circular_reference_sum),
                )
                return@launch
            }
            if (version != profileMutationVersion) return@launch
            if (replacingIndex < 0) {
                profiles.add(profile)
            } else {
                profiles[replacingIndex] = profile
            }
            uiState.update {
                it.copy(profiles = profiles)
            }
        }
    }

    private suspend fun ProxyEntity.canAdd(otherProfiles: List<ProxyEntity>): Boolean {
        if (containsProfileReference(editingId, includeGroupProxies = false)) return false
        for (existingProfile in otherProfiles) {
            if (existingProfile.containsProfileReference(id, includeGroupProxies = false)) {
                return false
            }
        }
        if (
            !isNew && groupProxiesOverlapProfileReferences(
                groupId = proxyEntity.groupId,
                rootProfileId = editingId,
                memberProfiles = otherProfiles + this,
            )
        ) {
            return false
        }
        return true
    }

    override fun setCustomConfig(config: String) {
        uiState.update { it.copy(customConfig = config) }
    }

    override fun setCustomOutbound(outbound: String) {
        uiState.update { it.copy(customOutbound = outbound) }
    }

    fun setName(name: String) {
        uiState.update { it.copy(name = name) }
    }

    fun setManagement(management: Int) {
        uiState.update { it.copy(management = management) }
    }

    fun setInterruptExistConnections(interrupt: Boolean) {
        uiState.update { it.copy(interruptExistConnections = interrupt) }
    }

    fun setTestURL(url: String) {
        uiState.update { it.copy(testURL = url) }
    }

    fun setTestInterval(interval: String) {
        uiState.update { it.copy(testInterval = interval) }
    }

    fun setTestIdleTimeout(timeout: String) {
        uiState.update { it.copy(testIdleTimeout = timeout) }
    }

    fun setTestTolerance(tolerance: Int) {
        uiState.update { it.copy(testTolerance = tolerance) }
    }

    private var groupSelectionJob: Job? = null
    private var groupSelectionVersion = 0L

    private fun invalidateGroupSelection() {
        groupSelectionVersion++
        groupSelectionJob?.cancel()
        groupSelectionJob = null
    }

    private fun validateGroupSelection(
        groupId: Long,
        filter: String,
        onInvalid: () -> Unit = {},
    ) {
        val version = ++groupSelectionVersion
        groupSelectionJob?.cancel()
        groupSelectionJob = viewModelScope.launch {
            val filterRegex = try {
                filter.blankAsNull()?.toRegex()
            } catch (error: IllegalArgumentException) {
                if (version != groupSelectionVersion) return@launch
                onInvalid()
                emitInvalidRegex(error)
                return@launch
            }
            val groupProfiles = SagerDatabase.proxyDao.getByGroup(groupId).first()
            if (version != groupSelectionVersion) return@launch
            val selectedProfiles = groupProfiles.filter { profile ->
                profile.id != editingId &&
                    filterRegex?.containsMatchIn(profile.displayName()) != false
            }
            for (profile in selectedProfiles) {
                if (version != groupSelectionVersion) return@launch
                if (profile.containsProfileReference(editingId, includeGroupProxies = false)) {
                    if (version != groupSelectionVersion) return@launch
                    onInvalid()
                    emitAlert(
                        title = StringOrRes.Res(Res.string.circular_reference),
                        message = StringOrRes.Res(Res.string.circular_reference_sum),
                    )
                    return@launch
                }
            }
            if (
                !isNew && groupProxiesOverlapProfileReferences(
                    groupId = proxyEntity.groupId,
                    rootProfileId = editingId,
                    memberProfiles = selectedProfiles,
                )
            ) {
                if (version != groupSelectionVersion) return@launch
                onInvalid()
                emitAlert(
                    title = StringOrRes.Res(Res.string.circular_reference),
                    message = StringOrRes.Res(Res.string.circular_reference_sum),
                )
            }
        }
    }

    fun setCollectType(type: Int) {
        val state = uiState.value
        if (type != ProxySetBean.TYPE_GROUP || state.groupID <= 0L) {
            invalidateGroupSelection()
            uiState.update { it.copy(collectType = type) }
            return
        }
        uiState.update { it.copy(collectType = type) }
        validateGroupSelection(
            groupId = state.groupID,
            filter = state.filterNotRegex,
            onInvalid = {
                uiState.update { current ->
                    if (current.collectType == type) {
                        current.copy(collectType = state.collectType)
                    } else {
                        current
                    }
                }
            },
        )
    }

    fun setGroupID(id: Long) {
        val previousGroupId = uiState.value.groupID
        uiState.update { it.copy(groupID = id) }
        validateGroupSelection(
            groupId = id,
            filter = uiState.value.filterNotRegex,
            onInvalid = {
                uiState.update { current ->
                    if (current.groupID == id) {
                        current.copy(groupID = previousGroupId)
                    } else {
                        current
                    }
                }
            },
        )
    }

    fun setFilterNotRegex(regex: String) {
        val invalidRegex = regex.blankAsNull()?.let {
            runCatching { it.toRegex() }.exceptionOrNull()
        }
        if (invalidRegex != null) {
            invalidateGroupSelection()
            viewModelScope.launch {
                emitInvalidRegex(invalidRegex)
            }
            return
        }

        val state = uiState.value
        if (state.collectType != ProxySetBean.TYPE_GROUP || state.groupID <= 0L) {
            invalidateGroupSelection()
            uiState.update { it.copy(filterNotRegex = regex) }
            return
        }
        uiState.update { it.copy(filterNotRegex = regex) }
        validateGroupSelection(
            groupId = state.groupID,
            filter = regex,
            onInvalid = {
                uiState.update { current ->
                    if (current.filterNotRegex == regex) {
                        current.copy(filterNotRegex = state.filterNotRegex)
                    } else {
                        current
                    }
                }
            },
        )
    }

    private suspend fun emitInvalidRegex(error: Throwable) {
        emitAlert(
            title = StringOrRes.Res(Res.string.error_title),
            message = StringOrRes.Direct(
                error.message ?: "Invalid regular expression",
            ),
        )
    }
}
