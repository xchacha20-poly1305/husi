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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
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
    val activeConnectionCount: Int = 0,
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

    val showActivate = queryOptions.showsActiveConnections()
    val showClosed = queryOptions.showsClosedConnections()
}

private fun Byte.showsActiveConnections(): Boolean =
    this and DashboardState.SHOW_TRACKER_ACTIVELY != 0.toByte()

private fun Byte.showsClosedConnections(): Boolean =
    this and DashboardState.SHOW_TRACKER_CLOSED != 0.toByte()

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
private data class ConnectionQuery(
    val sortMode: Int,
    val isDescending: Boolean,
    val queryOptions: Byte,
    val search: String,
) {
    val showActive = queryOptions.showsActiveConnections()
    val showClosed = queryOptions.showsClosedConnections()
}

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
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
    private val closedConnectionOrder = ArrayDeque<String>()
    private val connectionSnapshot = MutableStateFlow<List<ConnectionDetailState>>(emptyList())

    /** The connection whose detail sheet is open, if any. */
    private var selectedUuid: String? = null

    private var latestGroups: List<Group> = emptyList()
    private var latestOutbounds: List<GroupItem> = emptyList()

    private val proxySetComparator = AtomicReference(buildProxySetComparator(ProxySetOrder.ORIGIN))

    companion object {
        private val LOOP_INTERVAL = 1000L.milliseconds
        private val LOOP_INTERVAL_SECONDS = LOOP_INTERVAL.toDouble(DurationUnit.SECONDS)

        private const val GROUP_URL_TEST_CONCURRENCY = 10

        internal const val MAX_CLOSED_CONNECTIONS = 1000

        private fun bytesPerSecond(intervalDelta: Long): Long {
            return (intervalDelta / LOOP_INTERVAL_SECONDS).toLong()
        }
    }

    init {
        val connectionQuery = combine(
            DataStore.trafficSortMode.flow(),
            DataStore.trafficDescending.flow(),
            DataStore.trafficConnectionQuery.flow(),
            snapshotFlow { searchTextFieldState.text.toString() },
        ) { sortMode, isDescending, queryOptions, search ->
            ConnectionQuery(sortMode, isDescending, queryOptions.toByte(), search)
        }.onEach { query ->
            uiState.update { state ->
                state.copy(
                    sortMode = query.sortMode,
                    isDescending = query.isDescending,
                    queryOptions = query.queryOptions,
                )
            }
        }
        viewModelScope.launch(computeDispatcher) {
            combine(connectionSnapshot, connectionQuery, ::Pair)
                .conflate()
                .collect { (snapshot, query) ->
                    val visible = visibleConnections(snapshot, query)
                    uiState.update { state ->
                        state.copy(
                            connections = visible,
                            activeConnectionCount = snapshot.count { !it.isClosed },
                        )
                    }
                }
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
        closedConnectionOrder.clear()
        connectionSnapshot.value = emptyList()
        latestGroups = emptyList()
        latestOutbounds = emptyList()
        uiState.update { state ->
            state.copy(
                connections = emptyList(),
                activeConnectionCount = 0,
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
        val isPause = !uiState.value.isPause
        uiState.update { state -> state.copy(isPause = isPause) }
        if (!isPause) publishConnections()
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

    private fun visibleConnections(
        snapshot: List<ConnectionDetailState>,
        query: ConnectionQuery,
    ): List<ConnectionDetailState> {
        val search = query.search
        return snapshot
            .filter { connection ->
                val show = if (connection.isClosed) query.showClosed else query.showActive
                show && (search.isEmpty() || connection.match(search))
            }
            .sortedWith(buildComparator(query.sortMode, query.isDescending))
    }

    private fun publishConnections() {
        if (uiState.value.isPause) return
        refreshSelectedConnection()
        connectionSnapshot.value = connections.values.toList()
    }

    private fun refreshSelectedConnection() {
        val uuid = selectedUuid ?: return
        val selected = connections[uuid]
        uiState.update { state -> state.copy(selectedConnection = selected) }
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
            state.copy(selectedConnection = uuid?.let(connections::get))
        }
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
            closedConnectionOrder.clear()
            for (event in events.eventsList) {
                if (!event.isNew()) continue
                val connection = event.connection ?: continue
                putConnection(event.id, connection.toDetailState())
            }
        } else {
            var changed = false
            for (event in events.eventsList) {
                if (handleConnectionEvent(event)) changed = true
            }
            if (!changed) return
        }
        evictOverflowClosedConnections()
        publishConnections()
    }

    private fun putConnection(id: String, connection: ConnectionDetailState) {
        connections[id] = connection
        if (connection.isClosed) closedConnectionOrder.addLast(id)
    }

    private fun evictOverflowClosedConnections() {
        while (closedConnectionOrder.size > MAX_CLOSED_CONNECTIONS) {
            val id = closedConnectionOrder.removeFirst()
            if (connections[id]?.isClosed == true) connections.remove(id)
        }
    }

    private fun handleConnectionEvent(event: ConnectionEvent): Boolean {
        when (event.type) {
            ConnectionEventType.CONNECTION_EVENT_NEW -> {
                val connection = event.connection ?: return false
                putConnection(event.id, connection.toDetailState())
                return true
            }

            ConnectionEventType.CONNECTION_EVENT_UPDATE -> {
                val id = event.id
                val current = connections[id] ?: return false
                val uplinkDelta = event.uplinkDelta
                val downlinkDelta = event.downlinkDelta
                val hasTraffic = uplinkDelta > 0L || downlinkDelta > 0L
                val wasIdle = current.uploadSpeed == 0L && current.downloadSpeed == 0L
                if (!hasTraffic && wasIdle) return false
                connections[id] = current.copy(
                    uploadTotal = current.uploadTotal + uplinkDelta,
                    downloadTotal = current.downloadTotal + downlinkDelta,
                    uploadSpeed = bytesPerSecond(uplinkDelta),
                    downloadSpeed = bytesPerSecond(downlinkDelta),
                )
                return true
            }

            ConnectionEventType.CONNECTION_EVENT_CLOSED -> {
                val closedAt = formatConnectionTime(event.closedAt)
                if (closedAt.isBlank()) return false
                val id = event.id
                val current = connections[id] ?: return false
                if (current.closedAt == closedAt) return false
                putConnection(
                    id,
                    current.copy(
                        closedAt = closedAt,
                        uploadSpeed = 0L,
                        downloadSpeed = 0L,
                    ),
                )
                return true
            }

            ConnectionEventType.UNRECOGNIZED -> return false
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
