package io.nekohasekai.sagernet.ui.profile

import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.internal.ProxySetBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.onDefaultDispatcher
import io.nekohasekai.sagernet.ui.StringOrRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ProfileSettingsUiState

internal class ProxySetSettingsViewModel : ProfileSettingsViewModel<ProxySetBean>() {

    private val _uiState = MutableStateFlow(ProxySetUiState())
    override val uiState = _uiState.asStateFlow()

    override fun createBean() = ProxySetBean().applyDefaultValues()

    override fun ProxySetBean.writeToUiState() {
        _uiState.update {
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
        val state = _uiState.value

        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound
        name = name
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

    private fun load(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        val groups = SagerDatabase.groupDao.allGroups()
        val groupMap = LinkedHashMap<Long, ProxyGroup>(groups.size)
        groups.associateByTo(groupMap) { it.id }
        _uiState.update {
            it.copy(groups = groupMap)
        }
        val proxyList = ArrayList<ProxyEntity>(ids.size)
        val profiles = ProfileManager.getProfiles(ids).associateBy { it.id }
        onDefaultDispatcher {
            for (id in ids) {
                proxyList.add(profiles[id] ?: continue)
                _uiState.emit(_uiState.value.copy(profiles = proxyList.toList()))
            }
        }
    }

    fun submitList(list: List<ProxyEntity>) {
        _uiState.update {
            it.copy(profiles = list)
        }
    }

    fun remove(index: Int) = viewModelScope.launch {
        val profiles = _uiState.value.profiles.toMutableList()
        profiles.removeAt(index)
        _uiState.update {
            it.copy(profiles = profiles)
        }
    }

    /** The profile index that is being replacing */
    var replacing = 0

    fun onSelectProfile(id: Long) = viewModelScope.launch {
        val profile = ProfileManager.getProfile(id)!!
        if (!profile.canAdd()) {
            emitAlert(
                title = StringOrRes.Res(R.string.circular_reference),
                message = StringOrRes.Res(R.string.circular_reference_sum),
            )
            return@launch
        }
        val profiles = _uiState.value.profiles.toMutableList()
        if (replacing == 0) {
            if (profiles.any { it.id == profile.id }) {
                emitAlert(
                    title = StringOrRes.Res(R.string.duplicate_name),
                    message = StringOrRes.Direct(profile.displayName()),
                )
                return@launch
            }
            profiles.add(profile)
        } else {
            if (profiles.filterIndexed { index, _ -> index != replacing }.any { it.id == profile.id }) {
                emitAlert(
                    title = StringOrRes.Res(R.string.duplicate_name),
                    message = StringOrRes.Direct(profile.displayName()),
                )
                replacing = 0
                return@launch
            }
            profiles[replacing] = profile
            replacing = 0
        }
        _uiState.update {
            it.copy(profiles = profiles)
        }
    }

    private fun ProxyEntity.canAdd(): Boolean {
        if (id == editingId) return false

        for (entity in _uiState.value.profiles) {
            if (testProfileContains(entity, this)) return false
        }

        return true
    }

    private fun testProfileContains(profile: ProxyEntity, anotherProfile: ProxyEntity): Boolean {
        if (profile.type != ProxyEntity.TYPE_CHAIN || anotherProfile.type != ProxyEntity.TYPE_CHAIN) return false
        if (profile.id == anotherProfile.id) return true
        val proxies = profile.chainBean!!.proxies
        if (proxies.contains(anotherProfile.id)) return true
        if (proxies.isNotEmpty()) {
            for (entity in ProfileManager.getProfiles(proxies)) {
                if (testProfileContains(entity, anotherProfile)) {
                    return true
                }
            }
        }
        return false
    }

    override fun setCustomConfig(config: String) {
        _uiState.update { it.copy(customConfig = config) }
    }

    override fun setCustomOutbound(outbound: String) {
        _uiState.update { it.copy(customOutbound = outbound) }
    }

    fun setName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun setManagement(management: Int) {
        _uiState.update { it.copy(management = management) }
    }

    fun setInterruptExistConnections(interrupt: Boolean) {
        _uiState.update { it.copy(interruptExistConnections = interrupt) }
    }

    fun setTestURL(url: String) {
        _uiState.update { it.copy(testURL = url) }
    }

    fun setTestInterval(interval: String) {
        _uiState.update { it.copy(testInterval = interval) }
    }

    fun setTestIdleTimeout(timeout: String) {
        _uiState.update { it.copy(testIdleTimeout = timeout) }
    }

    fun setTestTolerance(tolerance: Int) {
        _uiState.update { it.copy(testTolerance = tolerance) }
    }

    fun setCollectType(type: Int) {
        _uiState.update { it.copy(collectType = type) }
    }

    fun setGroupID(id: Long) {
        _uiState.update { it.copy(groupID = id) }
    }

    fun setFilterNotRegex(regex: String) {
        _uiState.update { it.copy(filterNotRegex = regex) }
    }
}