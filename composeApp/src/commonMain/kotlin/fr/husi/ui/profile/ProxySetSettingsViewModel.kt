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
internal sealed interface ProviderUiItem {

    val key: Long

    fun toProvider(): ProxySetBean.Provider

    data class Profile(override val key: Long, val entity: ProxyEntity) : ProviderUiItem {
        override fun toProvider() = ProxySetBean.Provider.Single(entity.id)
    }

    data class Group(
        override val key: Long,
        val groupID: Long,
        val filterNotRegex: String,
    ) : ProviderUiItem {
        override fun toProvider() = ProxySetBean.Provider.Group(groupID, filterNotRegex)
    }
}

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

    val providers: List<ProviderUiItem> = emptyList(),
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
            )
        }
        load(providers)
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
        providers = state.providers.map { it.toProvider() }
    }

    private var nextItemKey = 0L

    private fun newItemKey() = nextItemKey++

    private suspend fun load(providers: List<ProxySetBean.Provider>) {
        val groups = SagerDatabase.groupDao.allGroups().first()
        val groupMap = LinkedHashMap<Long, ProxyGroup>(groups.size)
        groups.associateByTo(groupMap) { it.id }

        val singleIDs = providers.filterIsInstance<ProxySetBean.Provider.Single>().map { it.id }
        val profiles = ProfileManager.getProfiles(singleIDs).associateBy { it.id }
        val items = onDefaultDispatcher {
            val items = ArrayList<ProviderUiItem>(providers.size)
            for (provider in providers) {
                when (provider) {
                    is ProxySetBean.Provider.Single -> {
                        val profile = profiles[provider.id] ?: continue
                        items.add(ProviderUiItem.Profile(newItemKey(), profile))
                    }

                    is ProxySetBean.Provider.Group -> items.add(
                        ProviderUiItem.Group(
                            key = newItemKey(),
                            groupID = provider.groupID,
                            filterNotRegex = provider.filterNotRegex,
                        ),
                    )
                }
            }
            items
        }
        uiState.update {
            it.copy(groups = groupMap, providers = items)
        }
    }

    fun submitReorder(changes: List<OrderedItem<ProviderUiItem>>) {
        invalidateProviderMutation()
        val current = uiState.value.providers
        val changesMap = changes.associate { it.value.key to it.newIndex }

        val reordered = current.sortedBy { item ->
            changesMap[item.key] ?: current.indexOf(item)
        }

        uiState.update {
            it.copy(providers = reordered)
        }
    }

    fun remove(index: Int) {
        invalidateProviderMutation()
        val providers = uiState.value.providers.toMutableList()
        if (index !in providers.indices) return
        providers.removeAt(index)
        uiState.update {
            it.copy(providers = providers)
        }
    }

    var replacing = -1

    private var mutationJob: Job? = null
    private var mutationVersion = 0L

    private fun invalidateProviderMutation() {
        mutationVersion++
        mutationJob?.cancel()
        mutationJob = null
    }

    private suspend fun currentMemberProfiles(excludeIndex: Int): List<ProxyEntity> {
        val providers = uiState.value.providers
        val members = mutableListOf<ProxyEntity>()
        for ((index, item) in providers.withIndex()) {
            if (index == excludeIndex) continue
            when (item) {
                is ProviderUiItem.Profile -> members.add(item.entity)
                is ProviderUiItem.Group -> members.addAll(item.toProvider().entities())
            }
        }
        return members.distinctBy { it.id }
    }

    fun onSelectProfile(id: Long) {
        val replacingIndex = replacing
        replacing = -1
        val version = ++mutationVersion
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            val profile = ProfileManager.getProfile(id)!!
            if (version != mutationVersion) return@launch
            val providers = uiState.value.providers.toMutableList()
            if (replacingIndex >= providers.size) return@launch
            val alreadySelected = providers.filterIndexed { index, item ->
                index != replacingIndex
                        && item is ProviderUiItem.Profile
                        && item.entity.id == id
            }
            if (alreadySelected.isNotEmpty()) {
                emitAlert(
                    title = StringOrRes.Res(Res.string.duplicate_name),
                    message = StringOrRes.Direct(profile.displayName()),
                )
                return@launch
            }
            val otherProfiles = currentMemberProfiles(replacingIndex)
            if (!profile.canAdd(otherProfiles)) {
                if (version != mutationVersion) return@launch
                emitAlert(
                    title = StringOrRes.Res(Res.string.circular_reference),
                    message = StringOrRes.Res(Res.string.circular_reference_sum),
                )
                return@launch
            }
            if (version != mutationVersion) return@launch
            if (replacingIndex < 0) {
                providers.add(ProviderUiItem.Profile(newItemKey(), profile))
            } else {
                providers[replacingIndex] =
                    ProviderUiItem.Profile(providers[replacingIndex].key, profile)
            }
            uiState.update {
                it.copy(providers = providers)
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

    fun addGroupProvider(groupID: Long, filterNotRegex: String) {
        submitGroupProvider(index = -1, groupID = groupID, filterNotRegex = filterNotRegex)
    }

    fun setGroupProvider(index: Int, groupID: Long, filterNotRegex: String) {
        submitGroupProvider(index = index, groupID = groupID, filterNotRegex = filterNotRegex)
    }

    private fun submitGroupProvider(index: Int, groupID: Long, filterNotRegex: String) {
        val filterRegex = try {
            filterNotRegex.blankAsNull()?.toRegex()
        } catch (error: IllegalArgumentException) {
            invalidateProviderMutation()
            viewModelScope.launch {
                emitInvalidRegex(error)
            }
            return
        }

        val duplicated = uiState.value.providers.filterIndexed { itemIndex, item ->
            itemIndex != index && item is ProviderUiItem.Group && item.groupID == groupID
        }
        if (duplicated.isNotEmpty()) {
            invalidateProviderMutation()
            viewModelScope.launch {
                emitAlert(
                    title = StringOrRes.Res(Res.string.duplicate_name),
                    message = StringOrRes.Direct(
                        uiState.value.groups[groupID]?.displayName().orEmpty(),
                    ),
                )
            }
            return
        }

        val version = ++mutationVersion
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            val groupProfiles = SagerDatabase.proxyDao.getByGroup(groupID).first()
            if (version != mutationVersion) return@launch
            val selectedProfiles = groupProfiles.filter { profile ->
                profile.id != editingId &&
                        filterRegex?.containsMatchIn(profile.displayName()) != false
            }
            for (profile in selectedProfiles) {
                if (version != mutationVersion) return@launch
                if (profile.containsProfileReference(editingId, includeGroupProxies = false)) {
                    if (version != mutationVersion) return@launch
                    emitCircularReference()
                    return@launch
                }
            }
            val otherProfiles = currentMemberProfiles(index)
            if (
                !isNew && groupProxiesOverlapProfileReferences(
                    groupId = proxyEntity.groupId,
                    rootProfileId = editingId,
                    memberProfiles = otherProfiles + selectedProfiles,
                )
            ) {
                if (version != mutationVersion) return@launch
                emitCircularReference()
                return@launch
            }

            if (version != mutationVersion) return@launch
            uiState.update { state ->
                val providers = state.providers.toMutableList()
                if (index in providers.indices) {
                    providers[index] = ProviderUiItem.Group(
                        key = providers[index].key,
                        groupID = groupID,
                        filterNotRegex = filterNotRegex,
                    )
                } else {
                    providers.add(
                        ProviderUiItem.Group(
                            key = newItemKey(),
                            groupID = groupID,
                            filterNotRegex = filterNotRegex,
                        ),
                    )
                }
                state.copy(providers = providers)
            }
        }
    }

    private suspend fun emitCircularReference() {
        emitAlert(
            title = StringOrRes.Res(Res.string.circular_reference),
            message = StringOrRes.Res(Res.string.circular_reference_sum),
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
