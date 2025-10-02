package io.nekohasekai.sagernet.ui.profile

import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.ktx.onDefaultDispatcher
import io.nekohasekai.sagernet.ui.StringOrRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ChainUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",

    val name: String = "",
    val profiles: List<ProxyEntity> = emptyList(),
) : ProfileSettingsUiState

internal class ChainSettingsViewModel : ProfileSettingsViewModel<ChainBean>() {
    private val _uiState = MutableStateFlow(ChainUiState())
    override val uiState = _uiState.asStateFlow()

    override fun createBean() = ChainBean()

    override fun ChainBean.writeToUiState() {
        _uiState.update {
            it.copy(
                name = name,
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
            )
        }
        load(proxies)
    }

    override fun ChainBean.loadFromUiState() {
        val state = _uiState.value

        name = state.name
        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound

        proxies = state.profiles.map { it.id }
    }

    private fun load(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        val proxyList = ArrayList<ProxyEntity>(ids.size)
        val profiles = ProfileManager.getProfiles(ids).associateBy { it.id }
        onDefaultDispatcher {
            for (id in ids) {
                proxyList.add(profiles[id] ?: continue)
                _uiState.update {
                    it.copy(profiles = proxyList.toList())
                }
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
    var replacing = -1

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
        if (replacing < 0) {
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
                replacing = -1
                return@launch
            }
            profiles[replacing] = profile
            replacing = -1
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
}