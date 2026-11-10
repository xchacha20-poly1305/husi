package fr.husi.ui.dashboard

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import fr.husi.TrafficSortMode
import fr.husi.core.formatConnectionTime
import fr.husi.database.DataStore
import fr.husi.proto.daemon.ClashMode
import fr.husi.proto.daemon.CloseConnectionRequest
import fr.husi.proto.daemon.Connection
import fr.husi.proto.daemon.SelectOutboundRequest
import fr.husi.proto.daemon.URLTestRequest
import fr.husi.remote.RemoteState
import fr.husi.resources.Res
import fr.husi.resources.web_remote_ip_unavailable
import fr.husi.resources.web_system_proxy_unavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class RemoteDashboardPresenter(
    private val state: RemoteState,
    private val scope: CoroutineScope,
) : DashboardPresenter {
    override val searchTextFieldState = TextFieldState()
    override val proxySetSearchTextFieldState = TextFieldState()
    override val proxyGroupSearchTextFieldState = TextFieldState()
    private var paused by mutableStateOf<DashboardState?>(null)
    private var selected by mutableStateOf<String?>(null)
    private var descending by mutableStateOf(false)
    private var selectedSortMode by mutableStateOf(TrafficSortMode.START)
    private var showActive by mutableStateOf(true)
    private var showClosed by mutableStateOf(false)
    private var groupOrder by mutableStateOf(ProxySetOrder.ORIGIN)
    private var searchMode by mutableStateOf(ProxySetSearchMode.Outbound)
    private var groupSearch by mutableStateOf<String?>(null)
    private var widgets by
        mutableStateOf(defaultDashboardWidgets())
    private var active by mutableStateOf(false)
    private var lastPresentation = state.dashboardState()

    init {
        scope.launch {
            DataStore.dashboardWidgets.flow().collect { widgets = decodeDashboardWidgets(it) }
        }
    }

    override val uiState = snapshotFlow {
        presentation()
    }
        .stateIn(scope, SharingStarted.Eagerly, presentation())

    private fun presentation(): DashboardState {
        if (!active) return lastPresentation
        val data = paused ?: state.dashboardState()
        val search = searchTextFieldState.text.toString()
        val connections =
            data.connections.filter { connection ->
                (if (connection.isClosed) showClosed else showActive) &&
                    listOf(
                            connection.host,
                            connection.src,
                            connection.dst,
                            connection.inbound,
                            connection.outbound,
                            connection.matchedRule,
                        )
                        .any { it.contains(search, ignoreCase = true) }
            }
        val sorted =
            when (selectedSortMode) {
                TrafficSortMode.INBOUND -> connections.sortedBy { it.inbound }
                TrafficSortMode.SRC -> connections.sortedBy { it.src }
                TrafficSortMode.DST -> connections.sortedBy { it.dst }
                TrafficSortMode.UPLOAD -> connections.sortedBy { it.uploadTotal }
                TrafficSortMode.DOWNLOAD -> connections.sortedBy { it.downloadTotal }
                TrafficSortMode.MATCHED_RULE -> connections.sortedBy { it.matchedRule }
                TrafficSortMode.UPLOAD_SPEED -> connections.sortedBy { it.uploadSpeed }
                TrafficSortMode.DOWNLOAD_SPEED -> connections.sortedBy { it.downloadSpeed }
                else -> connections.sortedBy { it.startedAt }
            }
        val query =
            ProxySetQuery(
                globalSearch = proxySetSearchTextFieldState.text.toString(),
                groupSearch = proxyGroupSearchTextFieldState.text.toString(),
                mode = searchMode,
                searchingProxyGroup = groupSearch,
            )
        val sets =
            data.proxySets
                .map { set ->
                    val items =
                        when (groupOrder) {
                            ProxySetOrder.BY_NAME -> set.items.sortedBy { it.tag }
                            ProxySetOrder.BY_DELAY ->
                                set.items.sortedBy {
                                    if (it.urlTestDelay > 0) it.urlTestDelay else Int.MAX_VALUE
                                }
                            else -> set.items
                        }
                    set.copy(items = items)
                }
                .filterBy(query)
        return data
            .copy(
                isPause = paused != null,
                sortMode = selectedSortMode,
                isDescending = descending,
                queryOptions = ((if (showActive) 1 else 0) or (if (showClosed) 2 else 0)).toByte(),
                isSearchingConnections = search.isNotEmpty(),
                connections = if (descending) sorted.reversed() else sorted,
                selectedConnection = data.connections.firstOrNull { it.uuid == selected },
                proxySets = sets,
                proxySetOrder = groupOrder,
                proxySetQuery = query,
                dashboardWidgets =
                    widgets.filter { entry ->
                        entry.widget != DashboardWidget.DirectSpeed &&
                            entry.widget != DashboardWidget.ExitIP &&
                            entry.widget != DashboardWidget.SourceAddress &&
                            entry.widget != DashboardWidget.SystemProxy &&
                            entry.widget != DashboardWidget.NetworkInterfaces
                    },
            )
            .also { lastPresentation = it }
    }

    override fun setDashboardActive(active: Boolean) {
        this.active = active
    }

    override suspend fun initialize(isConnected: Boolean) {
        if (!isConnected) selected = null
        paused = null
    }

    override suspend fun resolveProcessInfo(process: String?, uid: Int): ProcessInfo? = null

    override fun togglePause() {
        paused = if (paused == null) state.dashboardState() else null
    }

    override fun clearSearchQuery() {
        searchTextFieldState.setTextAndPlaceCursorAtEnd("")
    }

    override fun toggleProxySetSearchMode() {
        searchMode = searchMode.toggled()
    }

    override fun clearProxySetGlobalQuery() {
        proxySetSearchTextFieldState.setTextAndPlaceCursorAtEnd("")
    }

    override fun openProxyGroupSearch(id: String) {
        groupSearch = id
    }

    override fun closeProxyGroupSearch() {
        groupSearch = null
        proxyGroupSearchTextFieldState.setTextAndPlaceCursorAtEnd("")
    }

    override fun clearProxySetSearch() {
        clearProxySetGlobalQuery()
        closeProxyGroupSearch()
    }

    override fun setSortDescending(descending: Boolean) {
        this.descending = descending
    }

    override fun setSortMode(mode: Int) {
        selectedSortMode = mode
    }

    override fun setProxySetOrder(order: Int) {
        groupOrder = order
    }

    override fun setDashboardWidgets(entries: List<DashboardWidgetEntry>) {
        widgets = entries
        scope.launch { DataStore.dashboardWidgets.set(encodeDashboardWidgets(entries)) }
    }

    override fun resetDashboardWidgets() {
        setDashboardWidgets(defaultDashboardWidgets())
    }

    override fun setQueryActivate(queryActivate: Boolean) {
        showActive = queryActivate
    }

    override fun setQueryClosed(queryClosed: Boolean) {
        showClosed = queryClosed
    }

    override fun selectConnection(uuid: String?) {
        selected = uuid
    }

    override fun closeConnection(uuid: String) {
        state.call { closeConnection(CloseConnectionRequest(uuid)) }
    }

    override fun selectOutbound(groupName: String, tag: String) {
        state.call { selectOutbound(SelectOutboundRequest(groupName, tag)) }
    }

    override fun urlTestForSingle(tag: String) {
        state.call { urlTest(URLTestRequest(tag)) }
    }

    override fun urlTestForGroup(id: String) {
        state.call { urlTest(URLTestRequest(id)) }
    }

    override fun detectIP() {
        state.showError(Res.string.web_remote_ip_unavailable)
    }

    override fun resetNetwork() {
        state.call { closeAllConnections() }
    }

    override fun setClashMode(mode: String) {
        state.call { setClashMode(ClashMode(mode)) }
    }

    override fun setSystemProxyEnabled(enabled: Boolean) {
        state.showError(Res.string.web_system_proxy_unavailable)
    }
}

private fun RemoteState.dashboardState() =
    DashboardState(
        isRemote = true,
        txTotal = traffic.uploadTotal,
        rxTotal = traffic.downloadTotal,
        memory = traffic.memory,
        goroutines = traffic.goroutines,
        txRateProxy = traffic.upload,
        rxRateProxy = traffic.download,
        proxySpeedHistory = history.toList(),
        selectedClashMode = clashMode.currentMode,
        clashModes = clashMode.modeList,
        connections = connections.values.map { it.toConnectionState() },
        activeConnectionCount = connections.values.count { it.closedAt <= 0 },
        proxySets =
            groups.map { group ->
                ProxySet(
                    tag = group.tag,
                    displayType = group.type,
                    selectable = group.selectable,
                    selected = group.selected,
                    items =
                        group.items.map {
                            ProxyItem(
                                it.tag,
                                it.type,
                                it.urlTestDelay,
                            )
                        },
                )
            },
    )

private fun Connection.toConnectionState() =
    ConnectionDetailState(
        uuid = id,
        inbound = inbound,
        network = network,
        uploadTotal = uplinkTotal,
        downloadTotal = downlinkTotal,
        uploadSpeed = uplink,
        downloadSpeed = downlink,
        startedAt = formatConnectionTime(createdAt),
        closedAt = formatConnectionTime(closedAt),
        src = source,
        dst = destination,
        host = domain,
        matchedRule = rule,
        outbound = outbound,
        chain = chainList.joinToString(" => "),
        protocol = protocol.ifBlank { null },
        processes = processInfo?.let { process ->
            process.packageNames.ifEmpty {
                listOfNotNull(process.processPath.ifBlank { null })
            }
        }.orEmpty(),
        uid = processInfo?.userId ?: 0,
    )
