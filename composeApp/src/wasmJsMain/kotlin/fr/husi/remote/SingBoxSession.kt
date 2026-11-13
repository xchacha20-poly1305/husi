package fr.husi.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastCoerceAtLeast
import fr.husi.core.formatConnectionTime
import fr.husi.database.RemoteBackend
import fr.husi.ktx.Logs
import fr.husi.ktx.blankAsNull
import fr.husi.proto.daemon.ClashMode
import fr.husi.proto.daemon.ClashModeStatus
import fr.husi.proto.daemon.CloseConnectionRequest
import fr.husi.proto.daemon.Connection
import fr.husi.proto.daemon.Group
import fr.husi.proto.daemon.OpenConnectStatusUpdate
import fr.husi.proto.daemon.OpenVPNStatusUpdate
import fr.husi.proto.daemon.SelectOutboundRequest
import fr.husi.proto.daemon.ServiceStatus
import fr.husi.proto.daemon.SubscribeConnectionsRequest
import fr.husi.proto.daemon.SubscribeStatusRequest
import fr.husi.proto.daemon.TailscaleStatusUpdate
import fr.husi.proto.daemon.URLTestRequest
import fr.husi.proto.daemon.Version
import fr.husi.ui.dashboard.ConnectionDetailState
import fr.husi.ui.dashboard.DashboardState
import fr.husi.ui.dashboard.ProxyItem
import fr.husi.ui.dashboard.ProxySet
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class SingBoxSession(
    val client: RemoteClient,
    private val onNativeUpdate: (SingBoxSession) -> Unit = {},
) : RemoteSession() {
    override val backend = RemoteBackend.SING_BOX
    var coreVersion by mutableStateOf<Version?>(null)
        private set

    var startedAt by mutableStateOf<Long?>(null)
        private set

    var traffic by mutableStateOf(RemoteTraffic())
    val history = mutableStateListOf<Float>()
    val groups = mutableStateListOf<Group>()
    val connections = mutableStateMapOf<String, Connection>()
    var tailscale by mutableStateOf(TailscaleStatusUpdate())
    var openConnect by mutableStateOf(OpenConnectStatusUpdate())
    var openVPN by mutableStateOf(OpenVPNStatusUpdate())
    var clashMode by mutableStateOf(ClashModeStatus())
    var serviceStatus by mutableStateOf("")

    private companion object {
        val INITIAL_RETRY_DELAY = 1.seconds
        val MAX_RETRY_DELAY = 30.seconds
        val HEARTBEAT_INTERVAL = 5.seconds
    }

    override suspend fun connect(url: String, secret: String) {
        coreVersion = client.connect(url, secret)
        version = coreVersion?.version.orEmpty()
    }

    private fun publishDashboard() {
        dashboard = dashboardState()
    }

    override suspend fun select(group: String, name: String) {
        client.service.selectOutbound(SelectOutboundRequest(group, name))
    }

    override suspend fun testDelay(name: String): Int? {
        client.service.urlTest(URLTestRequest(name))
        return null
    }

    override suspend fun closeConnection(id: String) {
        client.service.closeConnection(CloseConnectionRequest(id))
    }

    override suspend fun closeConnections() {
        client.service.closeAllConnections()
    }

    override suspend fun setMode(mode: String) {
        client.service.setClashMode(ClashMode(mode))
    }

    override suspend fun clearLogs() {
        client.service.clearLogs()
        logs.clear()
    }

    override fun close() {
        client.close()
    }

    override suspend fun observe(intervals: Flow<Int>) =
        supervisorScope {
            val service = client.service
            try {
                subscribe(this, "SubscribeGroups", { service.subscribeGroups() }) {
                    groups.clear()
                    groups.addAll(it.group)
                }
                launch {
                    intervals.collectLatest { interval ->
                        coroutineScope {
                            if (interval <= 0) return@coroutineScope
                            val nanoseconds = interval.milliseconds.inWholeNanoseconds
                            subscribe(
                                this,
                                "SubscribeStatus",
                                { service.subscribeStatus(SubscribeStatusRequest(nanoseconds)) },
                            ) {
                                traffic = RemoteTraffic.fromStatus(it)
                                history.add((traffic.upload + traffic.download).toFloat())
                                if (history.size > 60) {
                                    history.removeAt(0)
                                }
                            }
                            subscribe(
                                this,
                                "SubscribeConnections",
                                {
                                    service.subscribeConnections(
                                        SubscribeConnectionsRequest(nanoseconds),
                                    )
                                },
                            ) { event ->
                                applyConnectionEvents(connections, event, keepClosed = true)
                                val closed = connections.entries.filter { it.value.closedAt > 0 }
                                for (entry in closed.take((closed.size - 1000).fastCoerceAtLeast(0))) {
                                    connections.remove(entry.key)
                                }
                            }
                        }
                    }
                }
                subscribe(this, "SubscribeLog", { service.subscribeLog() }) { update ->
                    if (update.reset) {
                        logs.clear()
                    }
                    logs.addAll(
                        update.messages.map { RemoteLogEntry(it.level.toString(), it.message) },
                    )
                    while (logs.size > 2000) {
                        logs.removeAt(0)
                    }
                }
                subscribe(
                    this,
                    "SubscribeTailscaleStatus",
                    { service.subscribeTailscaleStatus() },
                ) {
                    tailscale = it
                }
                subscribe(
                    this,
                    "SubscribeOpenConnectStatus",
                    { service.subscribeOpenConnectStatus() },
                ) {
                    openConnect = it
                    onNativeUpdate(this@SingBoxSession)
                }
                subscribe(this, "SubscribeOpenVPNStatus", { service.subscribeOpenVPNStatus() }) {
                    openVPN = it
                    onNativeUpdate(this@SingBoxSession)
                }
                subscribe(this, "SubscribeClashMode", { service.subscribeClashMode() }) {
                    clashMode = clashMode.copy(currentMode = it.mode)
                }
                subscribe(this, "SubscribeServiceStatus", { service.subscribeServiceStatus() }) {
                    val status = it.status
                    if (status !is ServiceStatus.Type.Unrecognized) {
                        serviceStatus = status.toString()
                    }
                }
                launch {
                    runCatching { service.getClashModeStatus() }
                        .onSuccess {
                            clashMode = it
                            publishDashboard()
                        }
                        .onFailure {
                            if (it is CancellationException) throw it
                            Logs.w("GetClashModeStatus failed", it)
                        }
                }
                while (isActive) {
                    val remoteStartedAt = try {
                        service.getStartedAt().startedAt.takeIf { it > 0L }
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        Logs.d("GetStartedAt unavailable", failure)
                        null
                    }
                    ensureActive()
                    startedAt = remoteStartedAt
                    delay(HEARTBEAT_INTERVAL)
                    service.getVersion()
                }
            } finally {
                coroutineContext.cancelChildren()
            }
        }

    private fun <T> subscribe(
        child: CoroutineScope,
        method: String,
        updates: () -> Flow<T>,
        receive: (T) -> Unit,
    ) {
        child.launch {
            var retryDelay = INITIAL_RETRY_DELAY
            while (isActive) {
                try {
                    updates().collect {
                        retryDelay = INITIAL_RETRY_DELAY
                        this@launch.ensureActive()
                        receive(it)
                        publishDashboard()
                    }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    ensureActive()
                    // Retry tools independently so older daemons remain usable.
                    Logs.w("RPC subscription $method failed; retrying in $retryDelay", failure)
                }
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
            }
        }
    }
}

private fun SingBoxSession.dashboardState() = DashboardState(
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
    proxySets = groups.map { group ->
        ProxySet(
            tag = group.tag,
            displayType = group.type,
            selectable = group.selectable,
            selected = group.selected,
            items = group.items.map {
                ProxyItem(
                    it.tag,
                    it.type,
                    it.urlTestDelay,
                )
            },
        )
    },
)

private fun Connection.toConnectionState() = ConnectionDetailState(
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
    protocol = protocol.blankAsNull(),
    processes = processInfo?.let { process ->
        process.packageNames.ifEmpty {
            listOfNotNull(process.processPath.blankAsNull())
        }
    }.orEmpty(),
    uid = processInfo?.userId ?: 0,
)
