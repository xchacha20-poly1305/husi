@file:OptIn(ExperimentalAtomicApi::class)

package fr.husi.ui.dashboard

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.TrafficSortMode
import fr.husi.bg.BackendState
import fr.husi.bg.DefaultNetworkListener
import fr.husi.bg.SpeedStats
import fr.husi.core.CoreClient
import fr.husi.core.formatConnectionTime
import fr.husi.core.isNew
import fr.husi.core.proxyDisplayName
import fr.husi.core.remote.RemoteControlManager
import fr.husi.core.urlTestOptions
import fr.husi.database.DataStore
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.Logs
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.proto.daemon.ConnectionEvent
import fr.husi.proto.daemon.ConnectionEventType
import fr.husi.proto.daemon.ConnectionEvents
import fr.husi.proto.daemon.Group
import fr.husi.proto.daemon.GroupItem
import fr.husi.proto.v1.URLTestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

@Immutable
data class DashboardState(
    // toolbar
    val isPause: Boolean = false,
    val sortMode: Int = TrafficSortMode.START,
    val isDescending: Boolean = false,
    val queryOptions: Byte = SHOW_TRACKER_ACTIVELY,

    val memory: Long = 0,
    val goroutines: Int = 0,
    val txRateProxy: Long = 0,
    val rxRateProxy: Long = 0,
    val txRateDirect: Long = 0,
    val rxRateDirect: Long = 0,
    val proxySpeedHistory: List<Float> = idleSpeedHistory(),
    val directSpeedHistory: List<Float> = idleSpeedHistory(),
    val ipv4: String? = null,
    val ipv6: String? = null,
    val selectedClashMode: String = "",
    val clashModes: List<String> = emptyList(),
    val networkInterfaces: List<NetworkInterfaceInfo> = emptyList(),

    val connections: List<ConnectionDetailState> = emptyList(),
    val filteredConnections: List<ConnectionDetailState> = emptyList(),
    val selectedConnection: ConnectionDetailState? = null,

    val proxySets: List<ProxySet> = emptyList(),
    val proxySetOrder: Int = 0,
    val isRemote: Boolean = false,

    val urlTestingTags: Map<String, Int> = emptyMap(),

    val dashboardWidgets: List<DashboardWidgetEntry> = defaultDashboardWidgets(),
) {
    companion object {
        const val SHOW_TRACKER_ACTIVELY: Byte = 1
        const val SHOW_TRACKER_CLOSED: Byte = 2
    }

    val showActivate = queryOptions and SHOW_TRACKER_ACTIVELY != 0.toByte()
    val showClosed = queryOptions and SHOW_TRACKER_CLOSED != 0.toByte()
}

@Immutable
data class NetworkInterfaceInfo(
    val name: String,
    val addresses: List<String>,
)

object ProxySetOrder {
    const val ORIGIN = 0
    const val BY_NAME = 1
    const val BY_DELAY = 2

    val values get() = listOf(ORIGIN, BY_NAME, BY_DELAY)
}

@Immutable
data class ProxySet(
    val tag: String = "",
    val id: String = tag,
    val displayType: String = "",
    val selectable: Boolean = false,
    var selected: String = "",
    var items: List<ProxyItem> = emptyList(),
    val urlTestProgress: GroupUrlTestProgress? = null,
    val isAll: Boolean = false,
) {
    val isTesting: Boolean
        get() = urlTestProgress != null
}

internal const val ALL_PROXY_SET_ID = "__all_proxy_set__"

internal fun allProxySet(items: List<ProxyItem>): ProxySet {
    return ProxySet(
        id = ALL_PROXY_SET_ID,
        tag = "All proxies",
        displayType = "All",
        items = items,
        isAll = true,
    )
}

@Immutable
data class GroupUrlTestProgress(
    val current: Int,
    val total: Int,
)

@Immutable
data class ProxyItem(
    val tag: String = "",
    val type: String = "",
    val urlTestDelay: Int = -1,
    val displayType: String = type,
)

@Stable
class DashboardViewModel(
    private val loadPlatformNetworkInfo: suspend () -> Triple<List<NetworkInterfaceInfo>, String?, String?>,
    coreClient: CoreClient? = null,
    private val remoteControl: RemoteControlManager? = null,
) : ViewModel() {
    private val coreClientOverride = coreClient

    private val coreClient: CoreClient
        get() = coreClientOverride
            ?: remoteControl?.activeClient?.value
            ?: GlobalContext.get().get()

    private val isRemote: Boolean
        get() = remoteControl?.isRemote == true
    val uiState: StateFlow<DashboardState>
        field = MutableStateFlow(DashboardState())

    val searchTextFieldState = TextFieldState()

    private val connections = LinkedHashMap<String, ConnectionDetailState>()

    /** The connection whose detail sheet is open, if any. */
    private var selectedUuid: String? = null

    private var latestGroups: List<Group> = emptyList()
    private var latestOutbounds: List<GroupItem> = emptyList()

    private var comparator = buildComparator(TrafficSortMode.START, false)
    private val proxySetComparator = AtomicReference(buildProxySetComparator(ProxySetOrder.ORIGIN))

    companion object {
        private val LOOP_INTERVAL = 1000L.milliseconds
        private val LOOP_INTERVAL_SECONDS = LOOP_INTERVAL.toDouble(DurationUnit.SECONDS)

        private const val GROUP_URL_TEST_CONCURRENCY = 10

        private fun bytesPerSecond(intervalDelta: Long): Long {
            return (intervalDelta / LOOP_INTERVAL_SECONDS).toLong()
        }
    }

    init {
        viewModelScope.launch {
            DataStore.trafficSortMode.flow().combine(
                DataStore.trafficDescending.flow(),
            ) { mode, isDescending ->
                mode to isDescending
            }.collectLatest { (mode, isDescending) ->
                comparator = buildComparator(mode, isDescending)
                uiState.update { state ->
                    state.copy(
                        sortMode = mode,
                        isDescending = isDescending,
                    )
                }
                updateConnectionsSnapshot()
            }
        }
        viewModelScope.launch {
            DataStore.trafficConnectionQuery.flow().collectLatest {
                uiState.update { state ->
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
            DataStore.proxySetOrder.flow()
                .collectLatest { order ->
                    proxySetComparator.store(buildProxySetComparator(order))
                    uiState.update { state ->
                        state.copy(proxySetOrder = order)
                    }
                    publishProxySets()
                }
        }
        viewModelScope.launch {
            DataStore.dashboardWidgets.flow()
                .collectLatest { stored ->
                    uiState.update { state ->
                        state.copy(dashboardWidgets = decodeDashboardWidgets(stored))
                    }
                }
        }
        viewModelScope.launch {
            DefaultNetworkListener.start(this@DashboardViewModel) {
                refreshNetworkInterfaces()
            }
            refreshNetworkInterfaces()
        }
        viewModelScope.launch {
            remoteControl?.session?.collect { session ->
                uiState.update { it.copy(isRemote = session != null) }
            }
        }
        viewModelScope.launch {
            BackendState.status.collect { status ->
                if (isRemote) return@collect
                if (!status.state.connected) {
                    resetSpeedState()
                }
            }
        }
        viewModelScope.launch {
            BackendState.speedUpdates.collect { speed ->
                if (isRemote) return@collect
                if (!BackendState.status.value.state.connected || speed == null) {
                    resetSpeedState()
                    return@collect
                }
                appendSpeed(speed)
            }
        }
    }

    private var statusJob: Job? = null
    private var groupsJob: Job? = null
    private var outboundsJob: Job? = null
    private var connectionsJob: Job? = null
    private var clashModeJob: Job? = null
    private val processInfoResolver = ProcessInfoResolver()

    suspend fun initialize(isConnected: Boolean) {
        statusJob?.cancel()
        groupsJob?.cancel()
        outboundsJob?.cancel()
        connectionsJob?.cancel()
        clashModeJob?.cancel()
        connections.clear()
        latestGroups = emptyList()
        latestOutbounds = emptyList()
        uiState.update { state ->
            state.copy(
                connections = emptyList(),
                filteredConnections = emptyList(),
                proxySets = emptyList(),
                selectedClashMode = "",
                clashModes = emptyList(),
                memory = 0,
                goroutines = 0,
                txRateProxy = 0,
                rxRateProxy = 0,
                txRateDirect = 0,
                rxRateDirect = 0,
                proxySpeedHistory = idleSpeedHistory(),
                directSpeedHistory = idleSpeedHistory(),
            )
        }
        if (!isConnected) return
        if (!isRemote) {
            BackendState.status.value.speed?.let(::appendSpeed)
        }

        statusJob = viewModelScope.launch {
            try {
                coreClient.subscribeStatus(LOOP_INTERVAL).collect { status ->
                    uiState.update { state ->
                        state.copy(
                            memory = status.memory,
                            goroutines = status.goroutines,
                        )
                    }
                    if (isRemote) {
                        appendSpeed(
                            SpeedStats(
                                txRateProxy = status.uplink,
                                rxRateProxy = status.downlink,
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                Logs.w("subscribe status", e)
            }
        }

        groupsJob = viewModelScope.launch {
            try {
                coreClient.subscribeGroups().collect { groups ->
                    latestGroups = groups.groupList
                    publishProxySets()
                }
            } catch (e: Exception) {
                Logs.w("subscribe groups", e)
            }
        }

        outboundsJob = viewModelScope.launch {
            try {
                coreClient.subscribeOutbounds().collect { list ->
                    latestOutbounds = list.outboundsList
                    publishProxySets()
                }
            } catch (e: Exception) {
                Logs.w("subscribe outbounds", e)
            }
        }

        connectionsJob = viewModelScope.launch {
            try {
                coreClient.subscribeConnections(LOOP_INTERVAL).collect { events ->
                    handleConnectionEvents(events)
                }
            } catch (e: Exception) {
                Logs.w("subscribe connections", e)
            }
        }

        clashModeJob = viewModelScope.launch {
            try {
                val status = coreClient.getClashModeStatus()
                uiState.update { state ->
                    state.copy(
                        clashModes = status.modeListList,
                        selectedClashMode = status.currentMode,
                    )
                }
            } catch (e: Exception) {
                Logs.w("query clash modes", e)
            }
            try {
                coreClient.subscribeClashMode().collect { mode ->
                    uiState.update { state ->
                        state.copy(selectedClashMode = mode.mode)
                    }
                }
            } catch (e: Exception) {
                Logs.w("subscribe clash mode", e)
            }
        }
    }

    override fun onCleared() {
        statusJob?.cancel()
        groupsJob?.cancel()
        outboundsJob?.cancel()
        connectionsJob?.cancel()
        clashModeJob?.cancel()
        runOnDefaultDispatcher {
            DefaultNetworkListener.stop(this@DashboardViewModel)
        }
        super.onCleared()
        processInfoResolver.clear()
    }

    fun togglePause() {
        uiState.update { state ->
            val newPause = !state.isPause
            if (newPause) {
                state.copy(isPause = true)
            } else {
                val all = buildConnections(state)
                val query = searchTextFieldState.text.toString()
                state.copy(
                    isPause = false,
                    connections = all,
                    filteredConnections = buildFilteredConnections(all, query),
                )
            }
        }
    }

    fun clearSearchQuery() {
        searchTextFieldState.setTextAndPlaceCursorAtEnd("")
    }

    fun setSortDescending(descending: Boolean) = runOnIoDispatcher {
        DataStore.trafficDescending.set(descending)
    }

    fun setSortMode(mode: Int) = runOnIoDispatcher {
        DataStore.trafficSortMode.set(mode)
    }

    private fun buildComparator(mode: Int, descending: Boolean): Comparator<ConnectionDetailState> {
        val primarySelector: (ConnectionDetailState) -> Comparable<*> = when (mode) {
            TrafficSortMode.START -> ConnectionDetailState::startedAt
            TrafficSortMode.INBOUND -> ConnectionDetailState::inbound
            TrafficSortMode.SRC -> ConnectionDetailState::src
            TrafficSortMode.DST -> ConnectionDetailState::dst
            TrafficSortMode.UPLOAD -> ConnectionDetailState::uploadTotal
            TrafficSortMode.DOWNLOAD -> ConnectionDetailState::downloadTotal
            TrafficSortMode.UPLOAD_SPEED -> ConnectionDetailState::uploadSpeed
            TrafficSortMode.DOWNLOAD_SPEED -> ConnectionDetailState::downloadSpeed
            TrafficSortMode.MATCHED_RULE -> ConnectionDetailState::matchedRule
            else -> throw IllegalArgumentException("Unsupported sort mode: $mode")
        }

        return if (descending) {
            compareByDescending(primarySelector).thenByDescending(ConnectionDetailState::uuid)
        } else {
            compareBy(primarySelector).thenBy(ConnectionDetailState::uuid)
        }
    }

    fun setProxySetOrder(order: Int) = viewModelScope.launch(Dispatchers.Default) {
        DataStore.proxySetOrder.set(order)
    }

    fun setDashboardWidgets(entries: List<DashboardWidgetEntry>) =
        viewModelScope.launch(Dispatchers.Default) {
            DataStore.dashboardWidgets.set(encodeDashboardWidgets(entries))
        }

    fun setQueryActivate(queryActivate: Boolean) = runOnIoDispatcher {
        val old = uiState.value.queryOptions
        DataStore.trafficConnectionQuery.set(
            if (queryActivate) {
                old or DashboardState.SHOW_TRACKER_ACTIVELY
            } else {
                old and DashboardState.SHOW_TRACKER_ACTIVELY.inv()
            }.toInt(),
        )
    }

    fun setQueryClosed(queryClosed: Boolean) = runOnIoDispatcher {
        val old = uiState.value.queryOptions
        DataStore.trafficConnectionQuery.set(
            if (queryClosed) {
                old or DashboardState.SHOW_TRACKER_CLOSED
            } else {
                old and DashboardState.SHOW_TRACKER_CLOSED.inv()
            }.toInt(),
        )
    }

    private fun setUrlTestProgress(group: String, progress: GroupUrlTestProgress?) {
        uiState.update { state ->
            state.copy(
                proxySets = state.proxySets.map {
                    if (it.id == group) {
                        it.copy(urlTestProgress = progress)
                    } else {
                        it
                    }
                },
            )
        }
    }

    private fun markUrlTesting(tag: String, testing: Boolean) {
        uiState.update { state ->
            val counts = state.urlTestingTags
            val next = (counts[tag] ?: 0) + if (testing) 1 else -1
            state.copy(
                urlTestingTags = if (next > 0) {
                    counts + (tag to next)
                } else {
                    counts - tag
                },
            )
        }
    }

    private suspend fun <T> withUrlTesting(tag: String, block: suspend () -> T): T {
        markUrlTesting(tag, true)
        try {
            return block()
        } finally {
            markUrlTesting(tag, false)
        }
    }

    private fun appendSpeed(speed: SpeedStats) {
        uiState.update { state ->
            state.copy(
                txRateProxy = speed.txRateProxy,
                rxRateProxy = speed.rxRateProxy,
                txRateDirect = speed.txRateDirect,
                rxRateDirect = speed.rxRateDirect,
                proxySpeedHistory = nextSpeedHistory(
                    state.proxySpeedHistory,
                    (speed.txRateProxy + speed.rxRateProxy).toFloat(),
                ),
                directSpeedHistory = nextSpeedHistory(
                    state.directSpeedHistory,
                    (speed.txRateDirect + speed.rxRateDirect).toFloat(),
                ),
            )
        }
    }

    private fun resetSpeedState() {
        uiState.update { state ->
            state.copy(
                txRateProxy = 0,
                rxRateProxy = 0,
                txRateDirect = 0,
                rxRateDirect = 0,
                proxySpeedHistory = idleSpeedHistory(),
                directSpeedHistory = idleSpeedHistory(),
            )
        }
    }

    private suspend fun refreshNetworkInterfaces() {
        val (interfaces, ipv4, ipv6) = loadPlatformNetworkInfo()
        uiState.update { state ->
            state.copy(
                networkInterfaces = interfaces,
                ipv4 = ipv4,
                ipv6 = ipv6,
            )
        }
    }

    private fun buildConnections(state: DashboardState): List<ConnectionDetailState> {
        val showActive = state.showActivate
        val showClosed = state.showClosed
        return connections.values
            .filter { connection ->
                val show = if (connection.isClosed) {
                    showClosed
                } else {
                    showActive
                }
                show
            }
            .sortedWith(comparator)
    }

    private fun buildFilteredConnections(
        all: List<ConnectionDetailState>,
        query: String,
    ): List<ConnectionDetailState> {
        if (query.isEmpty()) return all
        return all.filter { it.match(query) }
    }

    private fun updateConnectionsSnapshot() {
        uiState.update { state ->
            if (state.isPause) return
            val all = buildConnections(state)
            val query = searchTextFieldState.text.toString()
            state.copy(
                connections = all,
                filteredConnections = buildFilteredConnections(all, query),
                selectedConnection = selectedConnection(),
            )
        }
    }

    /**
     * Opens the detail of [uuid], or closes it when null.
     *
     * The value comes from the unfiltered snapshot, so an open detail survives a connection
     * being hidden by the status filter or by the search query.
     */
    fun selectConnection(uuid: String?) {
        selectedUuid = uuid
        uiState.update { state ->
            state.copy(selectedConnection = selectedConnection())
        }
    }

    private fun selectedConnection(): ConnectionDetailState? {
        return selectedUuid?.let { uuid -> connections[uuid] }
    }

    internal suspend fun resolveProcessInfo(process: String?, uid: Int): ProcessInfo? {
        if (isRemote) return null
        return withContext(Dispatchers.IO) {
            processInfoResolver.resolve(process, uid)
        }
    }

    private fun handleConnectionEvents(events: ConnectionEvents) {
        if (events.reset) {
            connections.clear()
            for (event in events.eventsList) {
                if (!event.isNew()) continue
                val connection = event.connection ?: continue
                connections[event.id] = connection.toDetailState()
            }
            updateConnectionsSnapshot()
            return
        }
        for (event in events.eventsList) {
            handleConnectionEvent(event)
        }
    }

    private fun handleConnectionEvent(event: ConnectionEvent) {
        when (event.type) {
            ConnectionEventType.CONNECTION_EVENT_NEW -> {
                val connection = event.connection ?: return
                connections[event.id] = connection.toDetailState()
                updateConnectionsSnapshot()
            }

            ConnectionEventType.CONNECTION_EVENT_UPDATE -> {
                val id = event.id
                val current = connections[id] ?: return
                val uplinkDelta = event.uplinkDelta
                val downlinkDelta = event.downlinkDelta
                val hasTraffic = uplinkDelta > 0L || downlinkDelta > 0L
                val wasIdle = current.uploadSpeed == 0L && current.downloadSpeed == 0L
                if (!hasTraffic && wasIdle) return
                val updated = current.copy(
                    uploadTotal = current.uploadTotal + uplinkDelta,
                    downloadTotal = current.downloadTotal + downlinkDelta,
                    uploadSpeed = bytesPerSecond(uplinkDelta),
                    downloadSpeed = bytesPerSecond(downlinkDelta),
                )
                connections[id] = updated
                updateConnectionSnapshot(updated)
            }

            ConnectionEventType.CONNECTION_EVENT_CLOSED -> {
                val closedAt = formatConnectionTime(event.closedAt)
                if (closedAt.isBlank()) return
                val id = event.id
                val current = connections[id] ?: return
                if (current.closedAt == closedAt) return
                connections[id] = current.copy(
                    closedAt = closedAt,
                    uploadSpeed = 0L,
                    downloadSpeed = 0L,
                )
                updateConnectionsSnapshot()
            }

            ConnectionEventType.UNRECOGNIZED -> {}
        }
    }

    private fun updateConnectionSnapshot(updated: ConnectionDetailState) {
        uiState.update { state ->
            if (state.isPause) return
            val show = if (updated.isClosed) {
                state.showClosed
            } else {
                state.showActivate
            }
            // Update connections (status-filtered only)
            val current = state.connections
            val index = current.indexOfFirst { it.uuid == updated.uuid }
            val newConnections = if (!show) {
                if (index < 0) current
                else current.toMutableList().also { it.removeAt(index) }
            } else if (index >= 0) {
                current.toMutableList().also { it[index] = updated }
            } else {
                current.toMutableList().also { it.add(updated) }
            }
            if (newConnections !== current) {
                (newConnections as? MutableList)?.sortWith(comparator)
            }
            // Update filteredConnections (status + search)
            val query = searchTextFieldState.text.toString()
            val matchesSearch = show && (query.isEmpty() || updated.match(query))
            val currentFiltered = state.filteredConnections
            val filteredIndex = currentFiltered.indexOfFirst { it.uuid == updated.uuid }
            val newFiltered = if (!matchesSearch) {
                if (filteredIndex < 0) currentFiltered
                else currentFiltered.toMutableList().also { it.removeAt(filteredIndex) }
            } else if (filteredIndex >= 0) {
                currentFiltered.toMutableList().also { it[filteredIndex] = updated }
            } else {
                currentFiltered.toMutableList().also { it.add(updated) }
            }
            if (newFiltered !== currentFiltered) {
                (newFiltered as? MutableList)?.sortWith(comparator)
            }
            val newSelected = if (updated.uuid == selectedUuid) {
                updated
            } else {
                state.selectedConnection
            }
            state.copy(
                connections = newConnections,
                filteredConnections = newFiltered,
                selectedConnection = newSelected,
            )
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
            || processes?.any { it.contains(query) } == true
            || uid.toString().contains(query)

    private fun buildProxySetComparator(order: Int): Comparator<ProxyItem>? {
        return when (order) {
            ProxySetOrder.BY_NAME -> compareBy { it.tag }
            ProxySetOrder.BY_DELAY -> compareBy {
                if (it.urlTestDelay > 0) {
                    it.urlTestDelay
                } else {
                    Int.MAX_VALUE
                }
            }

            else -> null
        }
    }

    private fun publishProxySets() {
        uiState.update { state ->
            val olds = state.proxySets
            val comparator = proxySetComparator.load()
            val fresh = latestGroups.map { group ->
                ProxySet(
                    tag = group.tag,
                    displayType = proxyDisplayName(group.type),
                    selectable = group.selectable,
                    selected = group.selected,
                    items = group.itemsList.map { item ->
                        ProxyItem(
                            tag = item.tag,
                            type = item.type,
                            urlTestDelay = item.urlTestDelay,
                            displayType = proxyDisplayName(item.type),
                        )
                    }.let { items ->
                        comparator?.let { items.sortedWith(it) } ?: items
                    },
                )
            }
            val allItems = latestOutbounds.map { item ->
                ProxyItem(
                    tag = item.tag,
                    type = item.type,
                    urlTestDelay = item.urlTestDelay,
                    displayType = proxyDisplayName(item.type),
                )
            }.let { items ->
                comparator?.let { items.sortedWith(it) } ?: items
            }
            val oldAll = olds.firstOrNull { it.isAll }
            val freshAll = allProxySet(allItems).copy(urlTestProgress = oldAll?.urlTestProgress)
            // Keep the previous instance while nothing changed, so the list does not recompose.
            val allSet = oldAll?.takeIf { it == freshAll } ?: freshAll
            if (fresh.isEmpty()) {
                return@update state.copy(proxySets = listOf(allSet))
            }
            val oldsByTag = olds.filterNot { it.isAll }.associateBy { it.tag }
            val result = fresh.map { item ->
                val old = oldsByTag[item.tag] ?: return@map item
                val merged = item.copy(urlTestProgress = old.urlTestProgress)
                if (merged == old) old else merged
            }
            state.copy(
                proxySets = buildList(result.size + 1) {
                    add(allSet)
                    addAll(result)
                },
            )
        }
    }

    fun closeConnection(uuid: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            coreClient.closeConnection(uuid)
        } catch (e: Exception) {
            Logs.w(e)
        }
    }

    fun selectOutbound(groupName: String, tag: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            coreClient.selectOutbound(groupName, tag)
        } catch (e: Exception) {
            Logs.w(e)
        }
    }

    private suspend fun testOptions() = urlTestOptions(
        DataStore.connectionTestUnifiedDelay.get(),
        DataStore.connectionTestIgnoreHandshakeTime.get(),
    )

    private suspend fun urlTestOne(tag: String, link: String, timeoutMs: Int, options: URLTestOptions) {
        withUrlTesting(tag) {
            // Remote may be a vanilla sing-box daemon (StartedService only).
            if (isRemote) {
                coreClient.daemonUrlTest(tag)
            } else {
                coreClient.urlTest(tag, link, timeoutMs, options)
            }
        }
    }

    fun urlTestForSingle(tag: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            urlTestOne(
                tag,
                DataStore.connectionTestURL.get(),
                DataStore.connectionTestTimeout.get(),
                testOptions(),
            )
        } catch (e: Exception) {
            Logs.w(e)
        }
    }

    fun urlTestForGroup(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val proxySets = uiState.value.proxySets
        val proxySet = proxySets.firstOrNull { it.id == id } ?: return@launch
        if (proxySet.isTesting) return@launch
        val items = expandUrlTestTargets(
            proxySet.items,
            proxySets.filterNot { it.isAll }.associate { it.tag to it.items },
        )
        if (items.isEmpty()) return@launch
        val testURL = DataStore.connectionTestURL.get()
        val testTimeout = DataStore.connectionTestTimeout.get()
        val options = testOptions()
        try {
            val nextItemIndex = AtomicInt(0)
            val finishedCount = AtomicInt(0)
            setUrlTestProgress(id, GroupUrlTestProgress(current = 0, total = items.size))
            coroutineScope {
                repeat(items.size.fastCoerceAtMost(GROUP_URL_TEST_CONCURRENCY)) {
                    launch {
                        while (true) {
                            val index = nextItemIndex.fetchAndAdd(1)
                            if (index >= items.size) break
                            val tag = items[index].tag
                            try {
                                urlTestOne(tag, testURL, testTimeout, options)
                            } catch (e: Exception) {
                                Logs.w(e)
                            }
                            setUrlTestProgress(
                                id,
                                GroupUrlTestProgress(
                                    current = finishedCount.addAndFetch(1),
                                    total = items.size,
                                ),
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logs.w(e)
        } finally {
            setUrlTestProgress(id, null)
        }
    }

    fun resetNetwork() = viewModelScope.launch(Dispatchers.IO) {
        if (isRemote) return@launch
        try {
            coreClient.resetNetwork()
        } catch (e: Exception) {
            Logs.w(e)
        }
    }

    fun setClashMode(mode: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            coreClient.setClashMode(mode)
        } catch (e: Exception) {
            Logs.w(e)
        }
    }
}

internal fun skipGroupUrlTest(item: ProxyItem): Boolean {
    return item.type == SingBoxOptions.TYPE_DIRECT || item.type == SingBoxOptions.TYPE_BLOCK
}

internal fun expandUrlTestTargets(
    items: List<ProxyItem>,
    members: Map<String, List<ProxyItem>>,
): List<ProxyItem> {
    val visited = mutableSetOf<String>()
    val leaves = mutableListOf<ProxyItem>()

    fun walk(item: ProxyItem) {
        if (!visited.add(item.tag)) return
        val nested = members[item.tag]
        if (nested == null) {
            if (!skipGroupUrlTest(item)) leaves += item
            return
        }
        for (member in nested) walk(member)
    }

    for (item in items) walk(item)
    return leaves
}

internal const val SPEED_HISTORY_SIZE = 30

private fun idleSpeedHistory(): List<Float> = List(SPEED_HISTORY_SIZE) { 0f }

private fun nextSpeedHistory(history: List<Float>, sample: Float): List<Float> {
    val sized = if (history.size == SPEED_HISTORY_SIZE) {
        history
    } else {
        idleSpeedHistory()
    }
    return sized.drop(1) + sample
}
