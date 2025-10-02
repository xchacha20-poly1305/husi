package io.nekohasekai.sagernet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class RouteSettingsActivityUiState(
    val name: String = "",
    val action: String = "",
    val domains: String = "",
    val ip: String = "",
    val port: String = "",
    val sourcePort: String = "",
    val network: Set<String> = emptySet(),
    val source: String = "",
    val protocol: Set<String> = emptySet(),
    val ssid: String = "",
    val bssid: String = "",
    val client: String = "",
    val clashMode: String = "",
    val networkType: Set<String> = emptySet(),
    val networkIsExpensive: Boolean = false,
    val overrideAddress: String = "",
    val overridePort: Int = 0,
    val tlsFragment: Boolean = false,
    val tlsRecordFragment: Boolean = false,
    val tlsFragmentFallbackDelay: String = "",
    val resolveStrategy: String = "",
    val resolveDisableCache: Boolean = false,
    val resolveRewriteTTL: Int = 0,
    val resolveClientSubnet: String = "",
    val sniffTimeout: String = "",
    val sniffers: Set<String> = emptySet(),
    val outbound: Long = RuleEntity.OUTBOUND_PROXY,
    val packages: Set<String> = emptySet(),
)

internal class RouteSettingsActivityViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RouteSettingsActivityUiState())
    val uiState = _uiState.asStateFlow()

    private var editingId = -1L
    val isNew get() = editingId < 0L

    val isDirty = uiState.map { currentState ->
        initialState != currentState
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )

    fun loadRule(id: Long) {
        editingId = id
        val entity = if (isNew) {
            RuleEntity()
        } else {
            SagerDatabase.rulesDao.getById(id)!!
        }
        _uiState.update {
            it.copy(
                name = entity.name,
                action = entity.action,
                domains = entity.domains,
                ip = entity.ip,
                port = entity.port,
                sourcePort = entity.sourcePort,
                network = entity.network,
                source = entity.source,
                protocol = entity.protocol,
                ssid = entity.ssid,
                bssid = entity.bssid,
                client = entity.clientType,
                clashMode = entity.clashMode,
                networkType = entity.networkType,
                networkIsExpensive = entity.networkIsExpensive,
                outbound = entity.outbound,
                packages = entity.packages,
                overrideAddress = entity.overrideAddress,
                overridePort = entity.overridePort,
                tlsFragment = entity.tlsFragment,
                tlsRecordFragment = entity.tlsRecordFragment,
                tlsFragmentFallbackDelay = entity.tlsFragmentFallbackDelay,
                resolveStrategy = entity.resolveStrategy,
                resolveDisableCache = entity.resolveDisableCache,
                resolveRewriteTTL = entity.resolveRewriteTTL,
                resolveClientSubnet = entity.resolveClientSubnet,
                sniffTimeout = entity.sniffTimeout,
                sniffers = entity.sniffers,
            ).also {
                initialState = it
            }
        }
    }

    private lateinit var initialState: RouteSettingsActivityUiState

    private fun RuleEntity.loadFromUiState(state: RouteSettingsActivityUiState) {
        name = state.name
        action = state.action
        domains = state.domains
        ip = state.ip
        port = state.port
        sourcePort = state.sourcePort
        network = state.network
        source = state.source
        protocol = state.protocol
        ssid = state.ssid
        bssid = state.bssid
        clientType = state.client
        clashMode = state.clashMode
        networkType = state.networkType
        networkIsExpensive = state.networkIsExpensive
        outbound = state.outbound
        packages = state.packages
        overrideAddress = state.overrideAddress
        overridePort = state.overridePort
        tlsFragment = state.tlsFragment
        tlsRecordFragment = state.tlsRecordFragment
        tlsFragmentFallbackDelay = state.tlsFragmentFallbackDelay
        resolveStrategy = state.resolveStrategy
        resolveDisableCache = state.resolveDisableCache
        resolveRewriteTTL = state.resolveRewriteTTL
        resolveClientSubnet = state.resolveClientSubnet
        sniffTimeout = state.sniffTimeout
        sniffers = state.sniffers
    }

    fun save() = runOnIoDispatcher {
        if (isNew) {
            ProfileManager.createRule(RuleEntity().apply {
                loadFromUiState(_uiState.value)
            })
        } else {
            val entity = SagerDatabase.rulesDao.getById(editingId)
            if (entity == null) {
                return@runOnIoDispatcher
            }
            ProfileManager.updateRule(entity.apply {
                loadFromUiState(_uiState.value)
            })
        }
    }

    fun deleteRule() = runOnIoDispatcher {
        if (!isNew) {
            ProfileManager.deleteRule(editingId)
        }
    }

    fun setName(name: String) = viewModelScope.launch {
        _uiState.update { it.copy(name = name) }
    }

    fun setAction(action: String) = viewModelScope.launch {
        _uiState.update { it.copy(action = action) }
    }

    fun setDomains(domains: String) = viewModelScope.launch {
        _uiState.update { it.copy(domains = domains) }
    }

    fun setIp(ip: String) = viewModelScope.launch {
        _uiState.update { it.copy(ip = ip) }
    }

    fun setPort(port: String) = viewModelScope.launch {
        _uiState.update { it.copy(port = port) }
    }

    fun setSourcePort(sourcePort: String) = viewModelScope.launch {
        _uiState.update { it.copy(sourcePort = sourcePort) }
    }

    fun setNetwork(network: Set<String>) = viewModelScope.launch {
        _uiState.update { it.copy(network = network) }
    }

    fun setSource(source: String) = viewModelScope.launch {
        _uiState.update { it.copy(source = source) }
    }

    fun setProtocol(protocol: Set<String>) = viewModelScope.launch {
        _uiState.update { it.copy(protocol = protocol) }
    }

    fun setSsid(ssid: String) = viewModelScope.launch {
        _uiState.update { it.copy(ssid = ssid) }
    }

    fun setBssid(bssid: String) = viewModelScope.launch {
        _uiState.update { it.copy(bssid = bssid) }
    }

    fun setClient(client: String) = viewModelScope.launch {
        _uiState.update { it.copy(client = client) }
    }

    fun setClashMode(clashMode: String) = viewModelScope.launch {
        _uiState.update { it.copy(clashMode = clashMode) }
    }

    fun setNetworkType(networkType: Set<String>) = viewModelScope.launch {
        _uiState.update { it.copy(networkType = networkType) }
    }

    fun setNetworkIsExpensive(networkIsExpensive: Boolean) = viewModelScope.launch {
        _uiState.update { it.copy(networkIsExpensive = networkIsExpensive) }
    }

    fun setOverrideAddress(overrideAddress: String) = viewModelScope.launch {
        _uiState.update { it.copy(overrideAddress = overrideAddress) }
    }

    fun setOverridePort(overridePort: Int) = viewModelScope.launch {
        _uiState.update { it.copy(overridePort = overridePort) }
    }

    fun setTlsFragment(tlsFragment: Boolean) = viewModelScope.launch {
        _uiState.update { it.copy(tlsFragment = tlsFragment) }
    }

    fun setTlsRecordFragment(tlsRecordFragment: Boolean) = viewModelScope.launch {
        _uiState.update { it.copy(tlsRecordFragment = tlsRecordFragment) }
    }

    fun setTlsFragmentFallbackDelay(tlsFragmentFallbackDelay: String) = viewModelScope.launch {
        _uiState.update { it.copy(tlsFragmentFallbackDelay = tlsFragmentFallbackDelay) }
    }

    fun setResolveStrategy(resolveStrategy: String) = viewModelScope.launch {
        _uiState.update { it.copy(resolveStrategy = resolveStrategy) }
    }

    fun setResolveDisableCache(resolveDisableCache: Boolean) = viewModelScope.launch {
        _uiState.update { it.copy(resolveDisableCache = resolveDisableCache) }
    }

    fun setResolveRewriteTTL(resolveRewriteTTL: Int) = viewModelScope.launch {
        _uiState.update { it.copy(resolveRewriteTTL = resolveRewriteTTL) }
    }

    fun setResolveClientSubnet(resolveClientSubnet: String) = viewModelScope.launch {
        _uiState.update { it.copy(resolveClientSubnet = resolveClientSubnet) }
    }

    fun setSniffTimeout(sniffTimeout: String) = viewModelScope.launch {
        _uiState.update { it.copy(sniffTimeout = sniffTimeout) }
    }

    fun setSniffers(sniffers: Set<String>) = viewModelScope.launch {
        _uiState.update { it.copy(sniffers = sniffers) }
    }

    fun setOutbound(outbound: Long) = viewModelScope.launch {
        _uiState.update { it.copy(outbound = outbound) }
    }

    fun setPackages(packages: Set<String>) = viewModelScope.launch {
        _uiState.update { it.copy(packages = packages) }
    }

}