package io.nekohasekai.sagernet.ui.dashboard

import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.ProxySet
import io.nekohasekai.sagernet.bg.DefaultNetworkListener
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.repository.repo
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libcore.Libcore

@Immutable
data class DashboardState(
    // toolbar
    val isPause: Boolean = false,
    val sortMode: Int = TrafficSortMode.START,
    val isDescending: Boolean = false,
    val queryOptions: Int = Libcore.ShowTrackerActively,

    val memory: Long = 0,
    val goroutines: Int = 0,
    val ipv4: String? = null,
    val ipv6: String? = null,
    val selectedClashMode: String = "",
    val clashModes: List<String> = emptyList(),
    val networkInterfaces: List<NetworkInterfaceInfo> = emptyList(),

    val connections: List<Connection> = emptyList(),

    val proxySets: List<ProxySet> = emptyList(),
) {
    val showActivate = queryOptions and Libcore.ShowTrackerActively != 0
    val showClosed = queryOptions and Libcore.ShowTrackerClosed != 0
}

@Immutable
data class NetworkInterfaceInfo(
    val name: String,
    val addresses: List<String>,
)

@Stable
class DashboardViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardState())
    val uiState = _uiState.asStateFlow()

    val searchTextFieldState = TextFieldState()

    companion object {
        private const val LOOP_INTERVAL = 1000L

        private const val PRIORITY_WIFI = 0
        private const val PRIORITY_CELLULAR = 1
        private const val PRIORITY_OTHER = 2
        private const val PRIORITY_VPN = 3
    }

    init {
        viewModelScope.launch {
            DataStore.configurationStore.intFlow(Key.TRAFFIC_SORT_MODE).combine(
                DataStore.configurationStore.booleanFlow(Key.TRAFFIC_DESCENDING),
            ) { mode, isDescending ->
                mode to isDescending
            }.collectLatest { (mode, isDescending) ->
                comparator = buildComparator(mode, isDescending)
                _uiState.update { state ->
                    state.copy(
                        sortMode = mode,
                        isDescending = isDescending,
                    )
                }
            }
        }
        viewModelScope.launch {
            DataStore.configurationStore.intFlow(Key.TRAFFIC_CONNECTION_QUERY).collectLatest {
                _uiState.update { state ->
                    state.copy(
                        queryOptions = it,
                    )
                }
            }
        }
        viewModelScope.launch {
            DefaultNetworkListener.start(this@DashboardViewModel) { network ->
                refreshNetworkInterfaces(network)
            }
            refreshNetworkInterfaces(repo.connectivity.activeNetwork)
        }
    }

    private var job: Job? = null

    fun initialize(service: ISagerNetService?) {
        job?.cancel()
        if (service == null) return
        job = viewModelScope.launch {
            while (isActive) {
                refreshStatus(service)
                delay(LOOP_INTERVAL)
            }
        }
    }

    override fun onCleared() {
        runOnDefaultDispatcher {
            DefaultNetworkListener.stop(this@DashboardViewModel)
        }
        super.onCleared()
    }

    fun togglePause() {
        _uiState.update { state ->
            state.copy(
                isPause = !state.isPause,
            )
        }
    }

    fun clearSearchQuery() {
        searchTextFieldState.setTextAndPlaceCursorAtEnd("")
    }

    fun setSortDescending(descending: Boolean) = runOnIoDispatcher {
        DataStore.trafficDescending = descending
    }

    fun setSortMode(mode: Int) = runOnIoDispatcher {
        DataStore.trafficSortMode = mode
    }

    private var comparator = buildComparator(TrafficSortMode.START, false)

    private fun buildComparator(mode: Int, descending: Boolean): Comparator<Connection> {
        val primarySelector: (Connection) -> Comparable<*> = when (mode) {
            TrafficSortMode.START -> Connection::start
            TrafficSortMode.INBOUND -> Connection::inbound
            TrafficSortMode.SRC -> Connection::src
            TrafficSortMode.DST -> Connection::dst
            TrafficSortMode.UPLOAD -> Connection::uploadTotal
            TrafficSortMode.DOWNLOAD -> Connection::downloadTotal
            TrafficSortMode.MATCHED_RULE -> Connection::matchedRule
            else -> throw IllegalArgumentException("Unsupported sort mode: $mode")
        }

        return if (descending) {
            compareByDescending(primarySelector).thenByDescending(Connection::uuid)
        } else {
            compareBy(primarySelector).thenBy(Connection::uuid)
        }
    }

    fun setQueryActivate(queryActivate: Boolean) = runOnIoDispatcher {
        val old = uiState.value.queryOptions
        DataStore.trafficConnectionQuery = if (queryActivate) {
            old or Libcore.ShowTrackerActively
        } else {
            old and Libcore.ShowTrackerActively.inv()
        }
    }

    fun setQueryClosed(queryClosed: Boolean) = runOnIoDispatcher {
        val old = uiState.value.queryOptions
        DataStore.trafficConnectionQuery = if (queryClosed) {
            old or Libcore.ShowTrackerClosed
        } else {
            old and Libcore.ShowTrackerClosed.inv()
        }
    }

    fun setTesting(group: String, isTesting: Boolean) = viewModelScope.launch {
        _uiState.update { state ->
            state.copy(
                proxySets = state.proxySets.map {
                    if (it.tag == group) {
                        it.copy(isTesting = isTesting)
                    } else {
                        it
                    }
                },
            )
        }
    }

    private suspend fun refreshNetworkInterfaces(network: Network?) {
        val (interfaces, ipv4, ipv6) = buildNetworkInfo(network)
        _uiState.update { state ->
            state.copy(
                networkInterfaces = interfaces,
                ipv4 = ipv4,
                ipv6 = ipv6,
            )
        }
    }

    private suspend fun buildNetworkInfo(network: Network?): Triple<List<NetworkInterfaceInfo>, String?, String?> {
        @Suppress("DEPRECATION") val networks = repo.connectivity.allNetworks.toList().ifEmpty {
            if (network != null) {
                listOf(network)
            } else {
                emptyList()
            }
        }
        if (networks.isEmpty()) return Triple(emptyList(), null, null)

        val interfaces = mutableListOf<Pair<Int, Pair<NetworkInterfaceInfo, List<InetAddress>>>>()
        val knownNames = mutableSetOf<String>()

        for (item in networks) {
            val capabilities = repo.connectivity.getNetworkCapabilities(item) ?: continue
            val linkProperties = repo.connectivity.getLinkProperties(item) ?: continue
            val interfaceName = linkProperties.interfaceName ?: continue
            if (!knownNames.add(interfaceName)) continue

            val addressPairs = loadInterfaceAddresses(interfaceName).ifEmpty {
                linkProperties.linkAddresses.map { linkAddress ->
                    linkAddress.address to linkAddress.prefixLength.toShort()
                }
            }
            val hosts = addressPairs.map { it.first }
            val addresses = addressPairs.map { (address, prefix) -> address.toPrefix(prefix) }
            val priority = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> PRIORITY_WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> PRIORITY_CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> PRIORITY_VPN
                else -> PRIORITY_OTHER
            }
            interfaces += priority to (NetworkInterfaceInfo(
                name = interfaceName,
                addresses = addresses,
            ) to hosts)
        }

        if (interfaces.none { it.first == PRIORITY_WIFI }) {
            findWifiInterfaceFallback(knownNames)?.let { interfaces += PRIORITY_WIFI to it }
        }

        if (interfaces.isEmpty()) return Triple(emptyList(), null, null)

        val sortedInterfaces = interfaces.sortedWith(
            compareBy<Pair<Int, Pair<NetworkInterfaceInfo, List<InetAddress>>>> { it.first }
                .thenBy { it.second.first.name },
        )

        var ipv4: String? = null
        var ipv6: String? = null
        val interfaceInfos = sortedInterfaces.map { it.second.first }
        val interfaceHosts = sortedInterfaces.map { it.second.second }
        interfaceHosts.forEach { hosts ->
            if (ipv4 != null && ipv6 != null) return@forEach
            if (ipv4 == null) {
                ipv4 = hosts.filterIsInstance<Inet4Address>()
                    .firstOrNull()
                    ?.hostAddress
            }
            if (ipv6 == null) {
                val ipv6Addresses = hosts.filterIsInstance<Inet6Address>()
                ipv6 = ipv6Addresses.firstOrNull { it.isGlobalIPv6() }?.hostAddress
                    ?: ipv6Addresses.firstOrNull()?.hostAddress
            }
        }

        return Triple(interfaceInfos, ipv4, ipv6)
    }

    private suspend fun loadInterfaceAddresses(interfaceName: String): List<Pair<InetAddress, Short>> {
        repeat(10) {
            val addresses = runCatching {
                NetworkInterface.getByName(interfaceName)?.interfaceAddresses?.map { it.address to it.networkPrefixLength }
            }.getOrNull()?.filter { it.first != null }
            if (!addresses.isNullOrEmpty()) return addresses
            delay(100)
        }
        return emptyList()
    }

    private suspend fun findWifiInterfaceFallback(
        knownNames: MutableSet<String>,
    ): Pair<NetworkInterfaceInfo, List<InetAddress>>? {
        val networkInterfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
            ?: return null
        while (networkInterfaces.hasMoreElements()) {
            val netInterface = networkInterfaces.nextElement()
            val name = netInterface.name ?: continue
            val normalizedName = name.lowercase()
            if (!normalizedName.startsWith("wlan") && !normalizedName.startsWith("wifi")) continue
            val active =
                runCatching { netInterface.isUp && !netInterface.isLoopback }.getOrDefault(false)
            if (!active) continue
            if (!knownNames.add(name)) continue
            val addressPairs = loadInterfaceAddresses(name).ifEmpty {
                netInterface.interfaceAddresses.map { it.address to it.networkPrefixLength }
                    .filter { it.first != null }
            }
            if (addressPairs.isEmpty()) continue
            val hosts = addressPairs.map { it.first }
            val addresses = addressPairs.map { (address, prefix) -> address.toPrefix(prefix) }
            return NetworkInterfaceInfo(
                name = name,
                addresses = addresses,
            ) to hosts
        }
        return null
    }

    private fun Inet6Address.isGlobalIPv6(): Boolean {
        val firstByte = address[0].toInt() and 0xFF
        if (firstByte == 0xfc || firstByte == 0xfd) return false // ULA
        return !(isLinkLocalAddress || isSiteLocalAddress || isLoopbackAddress || isAnyLocalAddress)
    }

    private fun InetAddress.toPrefix(prefix: Short): String {
        return if (this is Inet6Address) {
            "${InetAddress.getByAddress(address).hostAddress}/$prefix"
        } else {
            "$hostAddress/$prefix"
        }
    }

    private suspend fun refreshStatus(service: ISagerNetService) {
        _uiState.update { state ->
            val connections = if (state.isPause) {
                state.connections
            } else {
                loadConnections(service, state.queryOptions)
            }
            state.copy(
                memory = service.queryMemory(),
                goroutines = service.queryGoroutines(),
                selectedClashMode = service.clashMode,
                clashModes = service.clashModes,
                connections = connections,
                proxySets = loadProxySets(service, state.proxySets),
            )
        }
    }

    private suspend fun loadConnections(
        service: ISagerNetService,
        options: Int,
    ): List<Connection> {
        val query = searchTextFieldState.text.toString()
        return service.queryConnections(options).connections
            .let {
                if (query.isEmpty()) {
                    it
                } else {
                    it.filter { it.match(query) }
                }
            }
            .sortedWith(comparator)
    }

    private fun Connection.match(query: String) = dst.contains(query)
            || network.contains(query)
            || host.contains(query)
            || start.contains(query)
            || matchedRule.contains(query)
            || outbound.contains(query)
            || chain.contains(query)
            || protocol?.contains(query) == true
            || process?.contains(query) == true
            || uid.toString().contains(query)

    private suspend fun loadProxySets(
        service: ISagerNetService,
        olds: List<ProxySet>,
    ): List<ProxySet> {
        return service.queryProxySet()
            .map {
                olds.find { old ->
                    old.tag == it.tag
                }?.let { old ->
                    it.copy(
                        isTesting = old.isTesting,
                    )
                } ?: it
            }
    }
}
