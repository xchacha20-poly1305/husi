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
import fr.husi.fmt.internal.resolveMembers
import fr.husi.ktx.applyDefaultValues
import fr.husi.ktx.blankAsNull
import fr.husi.resources.Res
import fr.husi.resources.circular_reference
import fr.husi.resources.circular_reference_sum
import fr.husi.resources.duplicate_name
import fr.husi.resources.error_title
import fr.husi.ui.StringOrRes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    data class Members(override val key: Long, val proxySetID: Long) : ProviderUiItem {
        override fun toProvider() = ProxySetBean.Provider.Members(proxySetID)
    }
}

/** UI item of a provider that needs no loaded profile to be shown. */
private fun ProxySetBean.Provider.toCollectingUiItem(key: Long): ProviderUiItem = when (this) {
    is ProxySetBean.Provider.Group -> ProviderUiItem.Group(key, groupID, filterNotRegex)
    is ProxySetBean.Provider.Members -> ProviderUiItem.Members(key, proxySetID)
    is ProxySetBean.Provider.Single -> error("A single provider is shown with its profile")
}

@Immutable
internal data class ProxySetUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",
    val name: String = "",
    val management: Int = ProxySetBean.MANAGEMENT_SELECTOR,
    val interruptExistConnections: Boolean = false,

    val testURL: String = "",
    val interval: String = "",
    val testIdleTimeout: String = "",
    val testTolerance: Int = 50,

    val providers: ImmutableList<ProviderUiItem> = persistentListOf(),
    val groups: LinkedHashMap<Long, ProxyGroup> = LinkedHashMap(),
    /** Proxy sets other than the edited one, the choices for a members provider. */
    val proxySets: LinkedHashMap<Long, ProxyEntity> = LinkedHashMap(),
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
                interval = interval,
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
        interval = state.interval
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
        val proxySets = SagerDatabase.proxyDao.getByType(ProxyEntity.TYPE_PROXY_SET)
        val proxySetMap = LinkedHashMap<Long, ProxyEntity>(proxySets.size)
        proxySets.filter { it.id != editingId }.associateByTo(proxySetMap) { it.id }

        val singleIDs = providers.filterIsInstance<ProxySetBean.Provider.Single>().map { it.id }
        val profiles = ProfileManager.getProfiles(singleIDs).associateBy { it.id }
        val items = withContext(Dispatchers.Default) {
            val items = ArrayList<ProviderUiItem>(providers.size)
            for (provider in providers) {
                when (provider) {
                    is ProxySetBean.Provider.Single -> {
                        val profile = profiles[provider.id] ?: continue
                        items.add(ProviderUiItem.Profile(newItemKey(), profile))
                    }

                    is ProxySetBean.Provider.Group -> {
                        items.add(provider.toCollectingUiItem(newItemKey()))
                    }

                    is ProxySetBean.Provider.Members -> {
                        if (provider.proxySetID !in proxySetMap) continue
                        items.add(provider.toCollectingUiItem(newItemKey()))
                    }
                }
            }
            items
        }
        uiState.update { state ->
            state.copy(
                groups = groupMap,
                proxySets = proxySetMap,
                providers = items.toImmutableList(),
            )
        }
    }

    fun submitReorder(changes: List<OrderedItem<ProviderUiItem>>) {
        invalidateProviderMutation()

        uiState.update { state ->
            val current = state.providers
            val changesMap = changes.associate { it.value.key to it.newIndex }
            val reordered = current.sortedBy { item ->
                changesMap[item.key] ?: current.indexOf(item)
            }
            state.copy(providers = reordered.toImmutableList())
        }
    }

    fun remove(index: Int) {
        invalidateProviderMutation()
        uiState.update { state ->
            if (index !in state.providers.indices) return
            val providers = state.providers.toMutableList()
            providers.removeAt(index)
            state.copy(providers = providers.toImmutableList())
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

    private suspend fun resolveMembers(providers: List<ProxySetBean.Provider>): List<ProxyEntity> {
        return ProxySetBean().also { it.providers = providers }.resolveMembers(editingId)
    }

    private suspend fun currentMemberProfiles(excludeIndex: Int): List<ProxyEntity> {
        val providers = uiState.value.providers
            .filterIndexed { index, _ -> index != excludeIndex }
            .map { it.toProvider() }
        return resolveMembers(providers)
    }

    private suspend fun ProxyEntity.closesCycle(expanders: Set<Long>): Boolean {
        val references = profileReferenceIds()
        if (editingId in references) return true
        return expanders.any { it != id && it in references }
    }

    fun onSelectProfile(id: Long) {
        val replacingIndex = replacing
        replacing = -1
        val version = ++mutationVersion
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            val profile = ProfileManager.getProfile(id)!!
            if (version != mutationVersion) return@launch
            uiState.update { state ->
                val providers = state.providers.toMutableList()
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
                val expanders = proxySetsExpanding(editingId)
                if (!profile.canAdd(otherProfiles, expanders)) {
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
                state.copy(providers = providers.toImmutableList())
            }
        }
    }

    private suspend fun ProxyEntity.canAdd(
        otherProfiles: List<ProxyEntity>,
        expanders: Set<Long>,
    ): Boolean {
        if (closesCycle(expanders)) return false
        for (existingProfile in otherProfiles) {
            if (existingProfile.containsProfileReference(id)) {
                return false
            }
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

    fun setInterval(interval: String) {
        uiState.update { it.copy(interval = interval) }
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
        try {
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

        submitCollectingProvider(index, ProxySetBean.Provider.Group(groupID, filterNotRegex))
    }

    fun addMembersProvider(proxySetID: Long) {
        submitMembersProvider(index = -1, proxySetID = proxySetID)
    }

    fun setMembersProvider(index: Int, proxySetID: Long) {
        submitMembersProvider(index = index, proxySetID = proxySetID)
    }

    private fun submitMembersProvider(index: Int, proxySetID: Long) {
        val duplicated = uiState.value.providers.filterIndexed { itemIndex, item ->
            itemIndex != index && item is ProviderUiItem.Members && item.proxySetID == proxySetID
        }
        if (duplicated.isNotEmpty()) {
            invalidateProviderMutation()
            viewModelScope.launch {
                emitAlert(
                    title = StringOrRes.Res(Res.string.duplicate_name),
                    message = StringOrRes.Direct(
                        uiState.value.proxySets[proxySetID]?.displayName().orEmpty(),
                    ),
                )
            }
            return
        }

        submitCollectingProvider(index, ProxySetBean.Provider.Members(proxySetID))
    }

    private fun submitCollectingProvider(index: Int, candidate: ProxySetBean.Provider) {
        val version = ++mutationVersion
        mutationJob?.cancel()
        mutationJob = viewModelScope.launch {
            val expanders = proxySetsExpanding(editingId)
            if (version != mutationVersion) return@launch
            if (candidate is ProxySetBean.Provider.Members &&
                (candidate.proxySetID == editingId || candidate.proxySetID in expanders)
            ) {
                emitCircularReference()
                return@launch
            }
            for (profile in resolveMembers(listOf(candidate))) {
                if (version != mutationVersion) return@launch
                if (profile.closesCycle(expanders)) {
                    if (version != mutationVersion) return@launch
                    emitCircularReference()
                    return@launch
                }
            }

            if (version != mutationVersion) return@launch
            uiState.update { state ->
                val providers = state.providers.toMutableList()
                if (index in providers.indices) {
                    providers[index] = candidate.toCollectingUiItem(providers[index].key)
                } else {
                    providers.add(candidate.toCollectingUiItem(newItemKey()))
                }
                state.copy(providers = providers.toImmutableList())
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
