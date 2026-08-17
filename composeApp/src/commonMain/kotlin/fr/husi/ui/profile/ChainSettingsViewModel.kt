package fr.husi.ui.profile

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.ernestoyaquello.dragdropswipelazycolumn.OrderedItem
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.fmt.internal.ChainBean
import fr.husi.ktx.onDefaultDispatcher
import fr.husi.resources.Res
import fr.husi.resources.circular_reference
import fr.husi.resources.circular_reference_sum
import fr.husi.resources.duplicate_name
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
internal data class ChainUiState(
    override val customConfig: String = "",
    override val customOutbound: String = "",

    val name: String = "",
    val profiles: List<ProxyEntity> = emptyList(),
) : ProfileEditorUiState

@Stable
internal class ChainSettingsViewModel : ProfileEditorViewModel<ChainBean>() {
    override val uiState: StateFlow<ChainUiState>
        field = MutableStateFlow(ChainUiState())

    override fun createBean() = ChainBean()

    override suspend fun ChainBean.writeToUiState() {
        uiState.update {
            it.copy(
                name = name,
                customConfig = customConfigJson,
                customOutbound = customOutboundJson,
            )
        }
        load(proxies)
    }

    override fun ChainBean.loadFromUiState() {
        val state = uiState.value

        name = state.name
        customConfigJson = state.customConfig
        customOutboundJson = state.customOutbound

        proxies = state.profiles.map { it.id }
    }

    private suspend fun load(ids: List<Long>) {
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
}
