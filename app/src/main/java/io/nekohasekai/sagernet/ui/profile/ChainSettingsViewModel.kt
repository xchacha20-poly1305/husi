package io.nekohasekai.sagernet.ui.profile

import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.internal.ChainBean
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.ui.StringOrRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ChainSettingsUiState(
    val profiles: List<ProxyEntity> = emptyList(),
)

internal class ChainSettingsViewModel : ProfileSettingsViewModel<ChainBean>() {
    private val _uiState = MutableStateFlow(ChainSettingsUiState())
    val uiState = _uiState.asStateFlow()

    override fun createBean() = ChainBean()

    override fun ChainBean.writeToTempDatabase() {
        DataStore.profileName = name
        DataStore.serverProtocol = proxies.joinToString(",")
    }

    override fun ChainBean.loadFromTempDatabase() {
        name = DataStore.profileName
        proxies = _uiState.value.profiles.mapX { it.id }
        initializeDefaultValues()
    }

    init {
        viewModelScope.launch {
            load()
        }
    }

    private suspend fun load() = viewModelScope.launch(Dispatchers.IO) {
        val idList = DataStore.serverProtocol.split(",")
            .mapNotNull { it.blankAsNull()?.toLong() }
        val proxyList = ArrayList<ProxyEntity>(idList.size)
        val profiles = ProfileManager.getProfiles(idList).mapX { it.id to it }.toMap()
        for (id in idList) {
            proxyList.add(profiles[id] ?: continue)
            _uiState.emit(_uiState.value.copy(profiles = proxyList.toList()))
        }
    }

    suspend fun move(from: Int, to: Int) {
        val profiles = _uiState.value.profiles.toMutableList()
        val moved = profiles.removeAt(from)
        profiles.add(to, moved)
        _uiState.emit(_uiState.value.copy(profiles = profiles))
        dirty = true
    }

    suspend fun remove(index: Int) {
        val profiles = _uiState.value.profiles.toMutableList()
        profiles.removeAt(index)
        _uiState.emit(_uiState.value.copy(profiles = profiles))
        dirty = true
    }

    /** The profile index that is being replacing */
    var replacing = 0

    suspend fun onSelectProfile(id: Long) {
        val profile = ProfileManager.getProfile(id)!!
        if (!profile.canAdd()) {
            _uiEvent.emit(
                ProfileSettingsUiEvent.Alert(
                    title = StringOrRes.Res(R.string.circular_reference),
                    message = StringOrRes.Res(R.string.circular_reference_sum),
                )
            )
            return
        }
        val profiles = _uiState.value.profiles.toMutableList()
        if (replacing == 0) {
            profiles.add(profile)
        } else {
            profiles[replacing - 1] = profile
        }
        _uiState.emit(_uiState.value.copy(profiles = profiles))
        dirty = true
    }

    private fun ProxyEntity.canAdd(): Boolean {
        if (id == DataStore.editingId) return false

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
}