package io.nekohasekai.sagernet.ui.dashboard

import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.aidl.ProxySet
import io.nekohasekai.sagernet.aidl.toList
import io.nekohasekai.sagernet.bg.DefaultNetworkListener
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import io.nekohasekai.sagernet.ktx.toList
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.utils.LibcoreClientManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import libcore.Client
import libcore.ConnectionEvent
import libcore.Libcore
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or

@Immutable
data class DashboardState(
    // toolbar
    val isPause: Boolean = false,
    val sortMode: Int = TrafficSortMode.START,
    val isDescending: Boolean = false,
    val queryOptions: Byte = Libcore.ShowTrackerActively,

    val memory: Long = 0,
    val goroutines: Int = 0,
    val ipv4: String? = null,
    val ipv6: String? = null,
    val selectedClashMode: String = "",
    val clashModes: List<String> = emptyList(),
    val networkInterfaces: List<NetworkInterfaceInfo> = emptyList(),

    val connections: List<ConnectionDetailState> = emptyList(),

    val proxySets: List<ProxySet> = emptyList(),
) {
    val showActivate = queryOptions and Libcore.ShowTrackerActively != 0.toByte()
    val showClosed = queryOptions and Libcore.ShowTrackerClosed != 0.toByte()
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

    private val connections = LinkedHashMap<String, ConnectionDetailState>()
    private val proxySetsByTag = HashMap<String, ProxySet>()

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
                updateConnectionsSnapshot()
            }
        }
        viewModelScope.launch {
            DataStore.configurationStore.intFlow(Key.TRAFFIC_CONNECTION_QUERY).collectLatest {
                _uiState.update { state ->
                    state.copy(
                        queryOptions = it.toByte(),
                    )
                }
                updateConnectionsSnapshot()
            }
        }
        viewModelScope.launch {
            snapshotFlow { searchTextFieldState.text.toString() }
                .drop(1)
                .distinctUntilChanged()
                .collectLatest { updateConnectionsSnapshot() }
        }
        viewModelScope.launch {
            DefaultNetworkListener.start(this@DashboardViewModel) { network ->
                refreshNetworkInterfaces(network)
            }
            refreshNetworkInterfaces(repo.connectivity.activeNetwork)
        }
    }

    private var job: Job? = null
    private var subscriptionJob: Job? = null
    private var clashModeSubscriptionJob: Job? = null
    private val clientManager = LibcoreClientManager()

    fun initialize(isConnected: Boolean) = viewModelScope.launch {
        job?.cancel()
        subscriptionJob?.cancel()
        clashModeSubscriptionJob?.cancel()
        clientManager.close()
        connections.clear()
        _uiState.update { state ->
            state.copy(
                connections = emptyList(),
                selectedClashMode = "",
                clashModes = emptyList(),
            )
        }
        if (!isConnected) return@launch

        subscriptionJob = clientManager.subscribeConnectionEvents(viewModelScope) { event ->
            viewModelScope.launch {
                handleConnectionEvent(event)
            }
        }
        try {
            clientManager.withClient { client ->
                val iterator = client.queryConnections(_uiState.value.queryOptions)
                    ?: return@withClient
                while (iterator.hasNext()) {
                    val info = iterator.next() ?: continue
                    connections[info.uuid] = info.toDetailState()
                }
            }
            updateConnectionsSnapshot()
        } catch (e: Exception) {
            Logs.w("query connections", e)
        }
        clashModeSubscriptionJob = clientManager.subscribeClashMode(viewModelScope) { mode ->
            viewModelScope.launch {
                _uiState.update { state ->
                    state.copy(selectedClashMode = mode)
                }
            }
        }
        try {
            val clashModes = clientManager.withClient { client ->
                client.queryClashModes()?.toList() ?: emptyList()
            }
            _uiState.update { state ->
                state.copy(clashModes = clashModes)
            }
        } catch (e: Exception) {
            Logs.w("query clash modes", e)
        }

        job = viewModelScope.launch {
            while (isActive) {
                if (!refreshStatus()) break
                delay(LOOP_INTERVAL)
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
        subscriptionJob?.cancel()
        clashModeSubscriptionJob?.cancel()
        runBlocking {
            clientManager.close()
        }
        runOnDefaultDispatcher {
            DefaultNetworkListener.stop(this@DashboardViewModel)
        }
        super.onCleared()
    }

    fun togglePause() {
        _uiState.update { state ->
            val newPause = !state.isPause
            state.copy(
                isPause = newPause,
                connections = if (newPause) {
                    state.connections
                } else {
                    buildConnections(state)
                },
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

    private fun buildComparator(mode: Int, descending: Boolean): Comparator<ConnectionDetailState> {
        val primarySelector: (ConnectionDetailState) -> Comparable<*> = when (mode) {
            TrafficSortMode.START -> ConnectionDetailState::startedAt
            TrafficSortMode.INBOUND -> ConnectionDetailState::inbound
            TrafficSortMode.SRC -> ConnectionDetailState::src
            TrafficSortMode.DST -> ConnectionDetailState::dst
            TrafficSortMode.UPLOAD -> ConnectionDetailState::uploadTotal
            TrafficSortMode.DOWNLOAD -> ConnectionDetailState::downloadTotal
            TrafficSortMode.MATCHED_RULE -> ConnectionDetailState::matchedRule
            else -> throw IllegalArgumentException("Unsupported sort mode: $mode")
        }

        return if (descending) {
            compareByDescending(primarySelector).thenByDescending(ConnectionDetailState::uuid)
        } else {
            compareBy(primarySelector).thenBy(ConnectionDetailState::uuid)
        }
    }

    fun setQueryActivate(queryActivate: Boolean) = runOnIoDispatcher {
        val old = uiState.value.queryOptions
        DataStore.trafficConnectionQuery = if (queryActivate) {
            old or Libcore.ShowTrackerActively
        } else {
            old and Libcore.ShowTrackerActively.inv()
        }.toInt()
    }

    fun setQueryClosed(queryClosed: Boolean) = runOnIoDispatcher {
        val old = uiState.value.queryOptions
        DataStore.trafficConnectionQuery = if (queryClosed) {
            old or Libcore.ShowTrackerClosed
        } else {
            old and Libcore.ShowTrackerClosed.inv()
        }.toInt()
    }

    fun setTesting(group: String, isTesting: Boolean) = viewModelScope.launch {
        _uiState.update { state ->
            state.copy(
                proxySets = state.proxySets.map {
                    if (it.tag == group) {
                        val updated = it.copy(isTesting = isTesting)
                        proxySetsByTag[group] = updated
                        updated
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
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> PRIORITY_VPN
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> PRIORITY_WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> PRIORITY_CELLULAR
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

    /**
     * @return true to continue polling, false to stop.
     */
    private suspend fun refreshStatus(): Boolean {
        return try {
            clientManager.withClient { client ->
                _uiState.update { state ->
                    state.copy(
                        memory = client.queryMemory(),
                        goroutines = client.queryGoroutines(),
                        connections = state.connections,
                        proxySets = loadProxySets(client, state.proxySets),
                    )
                }
            }
            true
        } catch (e: Exception) {
            Logs.w("refreshStatus error: ${e.message}")
            false
        }
    }

    private fun buildConnections(state: DashboardState): List<ConnectionDetailState> {
        val query = searchTextFieldState.text.toString()
        val showActive = state.showActivate
        val showClosed = state.showClosed
        return connections.values
            .filter { connection ->
                val show = if (connection.isClosed) {
                    showClosed
                } else {
                    showActive
                }
                show && (query.isEmpty() || connection.match(query))
            }
            .sortedWith(comparator)
    }

    private fun updateConnectionsSnapshot() {
        _uiState.update { state ->
            if (state.isPause) return
            state.copy(connections = buildConnections(state))
        }
    }

    private fun handleConnectionEvent(event: ConnectionEvent) {
        when (event.type) {
            Libcore.ConnectionEventNew -> {
                val trackerInfo = event.trackerInfo ?: return
                connections[trackerInfo.uuid] = trackerInfo.toDetailState()
                updateConnectionsSnapshot()
            }

            Libcore.ConnectionEventUpdate -> {
                val uplinkDelta = event.uplinkDelta
                val downlinkDelta = event.downlinkDelta
                if (uplinkDelta == 0L && downlinkDelta == 0L) return
                val id = event.id
                val current = connections[id] ?: return
                val updated = current.copy(
                    uploadTotal = current.uploadTotal + uplinkDelta,
                    downloadTotal = current.downloadTotal + downlinkDelta,
                )
                connections[id] = updated
                updateConnectionSnapshot(updated)
            }

            Libcore.ConnectionEventClosed -> {
                val closedAt = event.closedAt
                if (closedAt.isBlank()) return
                val id = event.id
                val current = connections[id] ?: return
                if (current.closedAt == closedAt) return
                connections[id] = current.copy(closedAt = closedAt)
                updateConnectionsSnapshot()
            }
        }
    }

    private fun updateConnectionSnapshot(updated: ConnectionDetailState) {
        _uiState.update { state ->
            if (state.isPause) return
            val query = searchTextFieldState.text.toString()
            val show = if (updated.isClosed) {
                state.showClosed
            } else {
                state.showActivate
            }
            val matches = show && (query.isEmpty() || updated.match(query))
            val current = state.connections
            val index = current.indexOfFirst { it.uuid == updated.uuid }
            if (!matches) {
                if (index < 0) return@update state
                val newList = current.toMutableList()
                newList.removeAt(index)
                return@update state.copy(connections = newList)
            }
            val newList = if (index >= 0) {
                current.toMutableList().also { it[index] = updated }
            } else {
                current.toMutableList().also { it.add(updated) }
            }
            newList.sortWith(comparator)
            state.copy(connections = newList)
        }
    }

    private fun ConnectionDetailState.match(query: String) = dst.contains(query)
            || network.contains(query)
            || host.contains(query)
            || startedAt.contains(query)
            || matchedRule.contains(query)
            || outbound.contains(query)
            || chain.contains(query)
            || protocol?.contains(query) == true
            || process?.contains(query) == true
            || uid.toString().contains(query)

    private suspend fun loadProxySets(
        client: Client,
        olds: List<ProxySet>,
    ): List<ProxySet> {
        if (proxySetsByTag.isEmpty() && olds.isNotEmpty()) {
            for (old in olds) {
                proxySetsByTag[old.tag] = old
            }
        }
        val fresh = client.queryProxySets()?.toList().orEmpty()
        if (fresh.isEmpty()) {
            proxySetsByTag.clear()
            return emptyList()
        }
        val freshTags = HashSet<String>(fresh.size)
        val result = buildList(fresh.size) {
            for (item in fresh) {
                freshTags.add(item.tag)
                val old = proxySetsByTag[item.tag]
                val merged = if (old == null) {
                    item
                } else {
                    item.copy(isTesting = old.isTesting)
                }
                val reused = if (old != null && merged == old) {
                    old
                } else {
                    merged
                }
                proxySetsByTag[item.tag] = reused
                add(reused)
            }
        }
        proxySetsByTag.keys.retainAll(freshTags)
        return result
    }

    fun closeConnection(uuid: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            clientManager.withClient { client ->
                client.closeConnection(uuid)
            }
        } catch (e: Exception) {
            Logs.w("closeConnection error: ${e.message}")
        }
    }

    fun selectOutbound(groupName: String, tag: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            clientManager.withClient { client ->
                client.selectOutbound(groupName, tag)
            }
        } catch (e: Exception) {
            Logs.w("selectOutbound error: ${e.message}")
        }
    }

    suspend fun groupURLTest(tag: String, timeout: Int) {
        try {
            clientManager.withClient { client ->
                val link = DataStore.connectionTestURL
                client.groupTest(tag, link, timeout)
            }
        } catch (e: Exception) {
            Logs.w("groupURLTest error: ${e.message}")
        }
    }

    fun resetNetwork() = viewModelScope.launch(Dispatchers.IO) {
        try {
            clientManager.withClient { client ->
                client.resetNetwork()
            }
        } catch (e: Exception) {
            Logs.w("resetNetwork error: ${e.message}")
        }
    }

    fun setClashMode(mode: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            clientManager.withClient { client ->
                client.setClashMode(mode)
            }
        } catch (e: Exception) {
            Logs.w("setClashMode error: ${e.message}")
        }
    }
}
