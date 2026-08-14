package fr.husi.ui.dashboard

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.Key
import fr.husi.TrafficSortMode
import fr.husi.bg.DefaultNetworkListener
import fr.husi.core.CoreClient
import fr.husi.core.formatConnectionTime
import fr.husi.core.isClosed
import fr.husi.core.isNew
import fr.husi.core.isUpdate
import fr.husi.core.proxyDisplayName
import fr.husi.core.urlTestOptions
import fr.husi.database.DataStore
import fr.husi.ktx.Logs
import fr.husi.ktx.emptyAsNull
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.proto.daemon.ConnectionEvent
import fr.husi.proto.daemon.ConnectionEvents
import fr.husi.proto.daemon.Group
import fr.husi.proto.daemon.GroupItem
import fr.husi.utils.PackageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.GlobalContext
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class DashboardState(
    // toolbar
    val isPause: Boolean = false,
    val sortMode: Int = TrafficSortMode.START,
    val isDescending: Boolean = false,
    val queryOptions: Byte = SHOW_TRACKER_ACTIVELY,

    val memory: Long = 0,
    val goroutines: Int = 0,
    val ipv4: String? = null,
    val ipv6: String? = null,
    val selectedClashMode: String = "",
    val clashModes: List<String> = emptyList(),
    val networkInterfaces: List<NetworkInterfaceInfo> = emptyList(),

    val connections: List<ConnectionDetailState> = emptyList(),
    val filteredConnections: List<ConnectionDetailState> = emptyList(),
    val selectedConnection: ConnectionDetailState? = null,

    val proxySets: List<ProxySet> = emptyList(),
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

@Immutable
data class ProxySet(
    val tag: String = "",
    val id: String = tag,
    val type: String = "",
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
        type = "All",
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
)

internal data class ProcessInfo(
    val packageName: String,
    val label: String,
    val icon: Any? = null,
)

@Stable
class DashboardViewModel(
    private val loadPlatformNetworkInfo: suspend () -> Triple<List<NetworkInterfaceInfo>, String?, String?>,
    private val coreClient: CoreClient = GlobalContext.get().get(),
) : ViewModel() {
    val uiState: StateFlow<DashboardState>
        field = MutableStateFlow(DashboardState())

    val searchTextFieldState = TextFieldState()

    private val connections = LinkedHashMap<String, ConnectionDetailState>()

    /** The connection whose detail sheet is open, if any. */
    private var selectedUuid: String? = null

    private val proxySetsByTag = HashMap<String, ProxySet>()
    private var latestGroups: List<Group> = emptyList()
    private var latestOutbounds: List<GroupItem> = emptyList()

    companion object {
        private val LOOP_INTERVAL = 1000L.milliseconds
    }

    init {
        viewModelScope.launch {
            DataStore.configurationStore.intFlow(Key.TRAFFIC_SORT_MODE).combine(
                DataStore.configurationStore.booleanFlow(Key.TRAFFIC_DESCENDING),
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
            DataStore.configurationStore.intFlow(
                Key.TRAFFIC_CONNECTION_QUERY,
                DashboardState.SHOW_TRACKER_ACTIVELY.toInt(),
            ).collectLatest {
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
            DefaultNetworkListener.start(this@DashboardViewModel) {
                refreshNetworkInterfaces()
            }
            refreshNetworkInterfaces()
        }
    }

    private var statusJob: Job? = null
    private var groupsJob: Job? = null
    private var outboundsJob: Job? = null
    private var connectionsJob: Job? = null
    private var clashModeJob: Job? = null
    private val processLabelAccess = Mutex()
    private val processLabelCache = mutableMapOf<String, String>()
    private val processIconCache = mutableMapOf<String, Any>()
    private val processIconAccess = Mutex()

    suspend fun initialize(isConnected: Boolean) {
        statusJob?.cancel()
        groupsJob?.cancel()
        outboundsJob?.cancel()
        connectionsJob?.cancel()
        clashModeJob?.cancel()
        connections.clear()
        proxySetsByTag.clear()
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
            )
        }
        if (!isConnected) return

        statusJob = viewModelScope.launch {
            try {
                coreClient.subscribeStatus(LOOP_INTERVAL).collect { status ->
                    uiState.update { state ->
                        state.copy(
                            memory = status.memory,
                            goroutines = status.goroutines,
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
        runBlocking {
            processLabelAccess.withLock {
                processLabelCache.clear()
            }
            processIconAccess.withLock {
                processIconCache.clear()
            }
        }
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
            old or DashboardState.SHOW_TRACKER_ACTIVELY
        } else {
            old and DashboardState.SHOW_TRACKER_ACTIVELY.inv()
        }.toInt()
    }

    fun setQueryClosed(queryClosed: Boolean) = runOnIoDispatcher {
        val old = uiState.value.queryOptions
        DataStore.trafficConnectionQuery = if (queryClosed) {
            old or DashboardState.SHOW_TRACKER_CLOSED
        } else {
            old and DashboardState.SHOW_TRACKER_CLOSED.inv()
        }.toInt()
    }

    private fun setUrlTestProgress(group: String, progress: GroupUrlTestProgress?) {
        uiState.update { state ->
            state.copy(
                proxySets = state.proxySets.map {
                    if (it.id == group) {
                        val updated = it.copy(urlTestProgress = progress)
                        if (!it.isAll) {
                            proxySetsByTag[it.tag] = updated
                        }
                        updated
                    } else {
                        it
                    }
                },
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
        return onIoDispatcher {
            if (process.isNullOrBlank() && uid < 0) return@onIoDispatcher null
            PackageResolver.awaitLoad()
            val packageName = resolvePackageName(process, uid) ?: return@onIoDispatcher null
            if (!PackageResolver.isAppInstalled(packageName)) return@onIoDispatcher null
            val label = processLabelAccess.withLock {
                processLabelCache[packageName]
                    ?: PackageResolver.loadAppLabel(packageName)
                        ?.also { processLabelCache[packageName] = it }
            } ?: return@onIoDispatcher null
            val icon = processIconAccess.withLock {
                processIconCache[packageName]
                    ?: PackageResolver.loadAppIcon(packageName)
                        ?.also { processIconCache[packageName] = it }
            }
            ProcessInfo(packageName = packageName, label = label, icon = icon)
        }
    }

    private fun resolvePackageName(process: String?, uid: Int): String? {
        process.emptyAsNull()?.let { packageName ->
            if (PackageResolver.isAppInstalled(packageName)) {
                return packageName
            }
        }
        if (uid >= 0) {
            return PackageResolver.findPackagesForUid(uid)?.firstOrNull()
        }
        return null
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
        when {
            event.isNew() -> {
                val connection = event.connection ?: return
                connections[event.id] = connection.toDetailState()
                updateConnectionsSnapshot()
            }

            event.isUpdate() -> {
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

            event.isClosed() -> {
                val closedAt = formatConnectionTime(event.closedAt)
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

    private fun publishProxySets() {
        val olds = uiState.value.proxySets
        if (proxySetsByTag.isEmpty() && olds.isNotEmpty()) {
            for (old in olds) {
                if (!old.isAll) {
                    proxySetsByTag[old.tag] = old
                }
            }
        }
        val fresh = latestGroups.map { group ->
            ProxySet(
                tag = group.tag,
                type = proxyDisplayName(group.type),
                selectable = group.selectable,
                selected = group.selected,
                items = group.itemsList.map { item ->
                    ProxyItem(
                        tag = item.tag,
                        type = proxyDisplayName(item.type),
                        urlTestDelay = item.urlTestDelay,
                    )
                },
            )
        }
        val allItems = latestOutbounds.map { item ->
            ProxyItem(
                tag = item.tag,
                type = proxyDisplayName(item.type),
                urlTestDelay = item.urlTestDelay,
            )
        }
        val allSet = allProxySet(allItems).copy(
            urlTestProgress = olds.firstOrNull { it.isAll }?.urlTestProgress,
        )
        if (fresh.isEmpty()) {
            proxySetsByTag.clear()
            uiState.update { state -> state.copy(proxySets = listOf(allSet)) }
            return
        }
        val freshTags = HashSet<String>(fresh.size)
        val result = buildList(fresh.size) {
            for (item in fresh) {
                freshTags.add(item.tag)
                val old = proxySetsByTag[item.tag]
                val merged = if (old == null) {
                    item
                } else {
                    item.copy(urlTestProgress = old.urlTestProgress)
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
        uiState.update { state ->
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

    private fun testOptions() = urlTestOptions(
        DataStore.connectionTestUnifiedDelay,
        DataStore.connectionTestIgnoreHandshakeTime,
    )

    fun urlTestForSingle(tag: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            coreClient.urlTest(
                tag,
                DataStore.connectionTestURL,
                DataStore.connectionTestTimeout,
                testOptions(),
            )
        } catch (e: Exception) {
            Logs.w(e)
        }
    }

    fun urlTestForGroup(id: String) = viewModelScope.launch(Dispatchers.IO) {
        val proxySet = uiState.value.proxySets.firstOrNull { it.id == id } ?: return@launch
        if (proxySet.isTesting) return@launch
        val items = if (proxySet.isAll) {
            proxySet.items.filterNot(::skipGroupUrlTest)
        } else {
            proxySet.items.toList()
        }
        if (items.isEmpty()) return@launch
        val testURL = DataStore.connectionTestURL
        val testTimeout = DataStore.connectionTestTimeout
        val options = testOptions()
        try {
            for ((index, item) in items.withIndex()) {
                setUrlTestProgress(
                    id,
                    GroupUrlTestProgress(
                        current = index + 1,
                        total = items.size,
                    ),
                )
                try {
                    coreClient.urlTest(item.tag, testURL, testTimeout, options)
                } catch (e: Exception) {
                    Logs.w(e)
                }
            }
        } catch (e: Exception) {
            Logs.w(e)
        } finally {
            setUrlTestProgress(id, null)
        }
    }

    fun resetNetwork() = viewModelScope.launch(Dispatchers.IO) {
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
    return item.type == "Direct" || item.type == "Block"
}
