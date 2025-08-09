package io.nekohasekai.sagernet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceDataStore
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface RouteSettingsActivityUiEvent {
    object Finish : RouteSettingsActivityUiEvent
    object EmptyRouteDialog : RouteSettingsActivityUiEvent
    class RuleLoaded(val name: String, val packageName: String?) : RouteSettingsActivityUiEvent
}

internal class RouteSettingsActivityViewModel : ViewModel(),
    OnPreferenceDataStoreChangeListener {
    private var editingId = 0L

    private val _dirty = MutableStateFlow(false)
    val dirty = _dirty.asStateFlow()

    private val _uiEvent = MutableSharedFlow<RouteSettingsActivityUiEvent>(1)
    val uiEvent = _uiEvent.asSharedFlow()

    fun loadRule(id: Long, packageName: String?) {
        editingId = id
        DataStore.editingId = id
        viewModelScope.launch {
            val entity = if (id == 0L) {
                RuleEntity().apply {
                    if (!packageName.isNullOrBlank()) {
                        packages = setOf(packageName)
                    }
                }
            } else {
                SagerDatabase.rulesDao.getById(id)
            }

            if (entity == null) {
                _uiEvent.emit(RouteSettingsActivityUiEvent.Finish)
                return@launch
            }

            loadEntityIntoDataStore(entity)
            DataStore.profileCacheStore.registerChangeListener(this@RouteSettingsActivityViewModel)
            _uiEvent.emit(RouteSettingsActivityUiEvent.RuleLoaded(entity.name, packageName))
        }
    }

    private fun loadEntityIntoDataStore(entity: RuleEntity) {
        DataStore.routeName = entity.name
        DataStore.routeAction = entity.action
        DataStore.routeDomain = entity.domains
        DataStore.routeIP = entity.ip
        DataStore.routePort = entity.port
        DataStore.routeSourcePort = entity.sourcePort
        DataStore.routeNetwork = entity.network
        DataStore.routeSource = entity.source
        DataStore.routeProtocol = entity.protocol
        DataStore.routeOutboundRule = entity.outbound
        DataStore.routeSSID = entity.ssid
        DataStore.routeBSSID = entity.bssid
        DataStore.routeClient = entity.clientType
        DataStore.routeClashMode = entity.clashMode
        DataStore.routeNetworkType = entity.networkType
        DataStore.routeNetworkIsExpensive = entity.networkIsExpensive
        DataStore.routeOverrideAddress = entity.overrideAddress
        DataStore.routeOverridePort = entity.overridePort
        DataStore.routeTlsFragment = entity.tlsFragment
        DataStore.routeTlsRecordFragment = entity.tlsRecordFragment
        DataStore.routeTlsFragmentFallbackDelay = entity.tlsFragmentFallbackDelay
        DataStore.routeResolveStrategy = entity.resolveStrategy
        DataStore.routeResolveDisableCache = entity.resolveDisableCache
        DataStore.routeResolveRewriteTTL = entity.resolveRewriteTTL
        DataStore.routeResolveClientSubnet = entity.resolveClientSubnet
        DataStore.routeSniffTimeout = entity.sniffTimeout
        DataStore.routeSniffers = entity.sniffers
        DataStore.routeOutbound = when (entity.outbound) {
            RuleEntity.OUTBOUND_PROXY -> 0
            RuleEntity.OUTBOUND_DIRECT -> 1
            RuleEntity.OUTBOUND_BLOCK -> 2
            else -> 3
        }
        DataStore.routePackages = entity.packages
    }

    private fun storeEntityFromDataStore(entity: RuleEntity) {
        entity.name = DataStore.routeName
        entity.action = DataStore.routeAction
        entity.domains = DataStore.routeDomain
        entity.ip = DataStore.routeIP
        entity.port = DataStore.routePort
        entity.sourcePort = DataStore.routeSourcePort
        entity.network = DataStore.routeNetwork
        entity.source = DataStore.routeSource
        entity.protocol = DataStore.routeProtocol
        entity.clientType = DataStore.routeClient
        entity.ssid = DataStore.routeSSID
        entity.bssid = DataStore.routeBSSID
        entity.clashMode = DataStore.routeClashMode
        entity.networkType = DataStore.routeNetworkType
        entity.networkIsExpensive = DataStore.routeNetworkIsExpensive
        entity.overrideAddress = DataStore.routeOverrideAddress
        entity.overridePort = DataStore.routeOverridePort
        entity.tlsFragment = DataStore.routeTlsFragment
        entity.tlsRecordFragment = DataStore.routeTlsRecordFragment
        entity.tlsFragmentFallbackDelay = DataStore.routeTlsFragmentFallbackDelay
        entity.resolveStrategy = DataStore.routeResolveStrategy
        entity.resolveDisableCache = DataStore.routeResolveDisableCache
        entity.resolveRewriteTTL = DataStore.routeResolveRewriteTTL
        entity.resolveClientSubnet = DataStore.routeResolveClientSubnet
        entity.sniffTimeout = DataStore.routeSniffTimeout
        entity.sniffers = DataStore.routeSniffers
        entity.outbound = when (DataStore.routeOutbound) {
            0 -> RuleEntity.OUTBOUND_PROXY
            1 -> RuleEntity.OUTBOUND_DIRECT
            2 -> RuleEntity.OUTBOUND_BLOCK
            else -> DataStore.routeOutboundRule
        }
        entity.packages = DataStore.routePackages.filterTo(hashSetOf()) { it.isNotBlank() }

        if (editingId == 0L) {
            entity.enabled = true
        }
    }

    private fun needSave(): Boolean {
        if (!_dirty.value) return false
        if (DataStore.routePackages.isEmpty() &&
            DataStore.routeDomain.isBlank() &&
            DataStore.routeIP.isBlank() &&
            DataStore.routePort.isBlank() &&
            DataStore.routeSourcePort.isBlank() &&
            DataStore.routeNetwork.isBlank() &&
            DataStore.routeSource.isBlank() &&
            DataStore.routeProtocol.isEmpty() &&
            DataStore.routeClient.isEmpty() &&
            DataStore.routeSSID.isBlank() &&
            DataStore.routeBSSID.isBlank() &&
            DataStore.routeClashMode.isBlank() &&
            DataStore.routeNetworkType.isEmpty() &&
            DataStore.routeNetworkIsExpensive &&
            DataStore.routeOutbound == 0 &&
            DataStore.routeOverrideAddress.isBlank() &&
            DataStore.routeOverridePort == 0 &&
            !DataStore.routeTlsFragment &&
            !DataStore.routeTlsRecordFragment &&
            DataStore.routeTlsFragmentFallbackDelay.isBlank() &&
            DataStore.routeResolveStrategy.isBlank() &&
            !DataStore.routeResolveDisableCache &&
            DataStore.routeResolveRewriteTTL >= 0 &&
            DataStore.routeResolveClientSubnet.isBlank() &&
            DataStore.routeSniffTimeout.isBlank() &&
            DataStore.routeSniffers.isEmpty()
        ) {
            return false
        }
        return true
    }

    fun saveAndExit(setResult: () -> Unit = {}) {
        viewModelScope.launch {
            if (!needSave()) {
                _uiEvent.emit(RouteSettingsActivityUiEvent.EmptyRouteDialog)
                return@launch
            }

            if (editingId == 0L) {
                setResult()
                ProfileManager.createRule(RuleEntity().apply {
                    storeEntityFromDataStore(this)
                })
            } else {
                val entity = SagerDatabase.rulesDao.getById(editingId)
                if (entity == null) {
                    _uiEvent.emit(RouteSettingsActivityUiEvent.Finish)
                    return@launch
                }
                ProfileManager.updateRule(entity.apply {
                    storeEntityFromDataStore(this)
                })
            }
            _uiEvent.emit(RouteSettingsActivityUiEvent.Finish)
        }
    }

    fun deleteRule() = runOnDefaultDispatcher {
        _uiEvent.emit(RouteSettingsActivityUiEvent.Finish)
        if (editingId != 0L) {
            ProfileManager.deleteRule(editingId)
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (!_dirty.value) viewModelScope.launch {
            _dirty.emit(true)
        }
    }

    override fun onCleared() {
        DataStore.profileCacheStore.unregisterChangeListener(this)
        super.onCleared()
    }
}