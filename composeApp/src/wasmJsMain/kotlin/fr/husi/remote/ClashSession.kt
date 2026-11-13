@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.remote

import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost
import fr.husi.core.formatConnectionTime
import fr.husi.database.RemoteBackend
import fr.husi.ktx.Logs
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.joinAddress
import fr.husi.ui.clash.ClashRulesModel
import fr.husi.ui.dashboard.ConnectionDetailState
import fr.husi.ui.dashboard.ProxyItem
import fr.husi.ui.dashboard.ProxySet
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class ClashSession(
    private val url: String,
    private val secret: String,
    val api: ClashApi = ClashBridge(url, secret),
) : RemoteSession() {
    init {
        dashboard = dashboard.copy(
            supportsGoroutines = false,
            memoryAvailable = false,
        )
    }

    override val backend = RemoteBackend.CLASH
    val rules = ClashRulesModel(api)

    private var closed = false
    private val history = ArrayDeque<Float>()
    private val connectionTracker = ClashConnectionTracker()

    override suspend fun connect(url: String, secret: String) {
        version = api.request(ClashRequests.version).version

        check(version.isNotBlank()) { "Missing Clash version" }
    }

    override suspend fun observe(intervals: Flow<Int>) =
        supervisorScope {
            watch(this, ClashStreams.logs) { message ->
                val level = when (val value = message.level.uppercase()) {
                    "WARNING" -> "WARN"
                    "SILENT" -> "FATAL"
                    else -> value
                }

                logs.add(RemoteLogEntry(level, message.message))
                while (logs.size > 2000) {
                    logs.removeAt(0)
                }
            }

            launch {
                intervals.collectLatest { interval ->
                    coroutineScope {
                        if (interval <= 0) return@coroutineScope

                        watch(this, ClashStreams.traffic, interval) { sample ->
                            val up = sample.up
                            val down = sample.down

                            history.addLast((up + down).toFloat())
                            while (history.size > 60) {
                                history.removeFirst()
                            }

                            dashboard = dashboard.copy(
                                txRateProxy = up,
                                rxRateProxy = down,
                                proxySpeedHistory = history.toList(),
                            )
                        }

                        watch(this, ClashStreams.connections, interval) { snapshot ->
                            dashboard =
                                dashboard.copy(
                                    txTotal = snapshot.uploadTotal,
                                    rxTotal = snapshot.downloadTotal,
                                    connections = connectionTracker.apply(snapshot, nowMillis().toLong()),
                                    activeConnectionCount = snapshot.connections.size,
                                    memoryAvailable = dashboard.memoryAvailable || snapshot.memory != null,
                                    memory = snapshot.memory ?: dashboard.memory,
                                )
                        }

                        watch(this, ClashStreams.memory, interval) {
                            dashboard = dashboard.copy(
                                memory = it.inuse,
                                memoryAvailable = true,
                            )
                        }
                    }
                }
            }

            poll(this, "proxies", ::refreshProxies)
            poll(this, "config", ::refreshConfig)

            try {
                while (isActive) {
                    delay(5000.milliseconds)
                    api.request(ClashRequests.version)
                }
            } finally {
                coroutineContext.cancelChildren()
            }
        }

    private fun poll(
        scope: CoroutineScope,
        operation: String,
        read: suspend () -> Unit,
    ) = scope.launch {
        var wait = 1000L

        while (isActive) {
            try {
                read()
                wait = 1000L
                delay(5000.milliseconds)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Logs.w("Clash polling $operation failed; retry delay=${wait}ms", failure)
                if (failure is ClashApiException && failure.unsupported) return@launch
                delay(wait.milliseconds)
                wait = (wait * 2).fastCoerceAtMost(30000)
            }
        }
    }

    private fun <T> watch(
        scope: CoroutineScope,
        stream: ClashStream<T>,
        interval: Int = 1000,
        receive: (T) -> Unit,
    ) = scope.launch {
        var wait = 1000.milliseconds

        while (isActive) {
            try {
                api.watch(stream, interval).collect {
                    this@launch.ensureActive()
                    if (!closed) {
                        receive(it)
                    }
                    wait = 1000.milliseconds
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Logs.w("Clash stream ${stream.kind} failed; retry delay=$wait", failure)
                if (failure is ClashApiException && failure.unsupported) return@launch
            }
            delay(wait)
            wait = (wait * 2).coerceAtMost(30000.milliseconds)
        }
    }

    private suspend fun refreshProxies() {
        val proxies = api.request(ClashRequests.proxies)
        currentCoroutineContext().ensureActive()
        if (closed) return

        dashboard = dashboard.copy(
            proxySets = proxies.mapNotNull { (name, proxy) ->
                val members = proxy.members ?: return@mapNotNull null
                ProxySet(
                    tag = name,
                    displayType = proxy.type,
                    selected = proxy.selected,
                    selectable = proxy.selectable,
                    items = members.map { tag ->
                        val item = proxies[tag]
                        ProxyItem(tag = tag, type = item?.type.orEmpty(), urlTestDelay = item?.delay ?: -1)
                    },
                )
            },
        )
    }

    internal suspend fun refreshConfig() {
        val config = api.request(ClashRequests.config)
        currentCoroutineContext().ensureActive()
        if (closed) return

        dashboard = dashboard.copy(selectedClashMode = config.mode, clashModes = config.availableModes)
    }

    override suspend fun select(group: String, name: String) {
        api.request(ClashRequests.select(group, name))
        refreshProxies()
    }

    override suspend fun testDelay(name: String): Int {
        val delay = api.request(ClashRequests.delay(name)).delay
        currentCoroutineContext().ensureActive()
        if (!closed) {
            dashboard = dashboard.copy(
                proxySets = dashboard.proxySets.map { group ->
                    group.copy(
                        items = group.items.map {
                            if (it.tag == name) {
                                it.copy(urlTestDelay = delay)
                            } else {
                                it
                            }
                        },
                    )
                },
            )
        }
        return delay
    }

    override suspend fun closeConnection(id: String) {
        api.request(ClashRequests.closeConnection(id))
    }

    override suspend fun closeConnections() {
        api.request(ClashRequests.closeConnections)
    }

    override suspend fun setMode(mode: String) {
        api.request(ClashRequests.setMode(mode))
        refreshConfig()
    }

    override suspend fun clearLogs() {
        logs.clear()
    }

    override fun close() {
        closed = true
        api.close()
    }
}

/** Connection snapshots contain cumulative bytes; rates use elapsed time between snapshots. */
internal class ClashConnectionTracker {
    private var sampledAt: Long? = null
    private var previous = emptyMap<String, Pair<Long, Long>>()
    private val records = linkedMapOf<String, ConnectionDetailState>()

    fun apply(snapshot: ClashConnections, now: Long): List<ConnectionDetailState> {
        val elapsed = sampledAt?.let { now - it } ?: 0L
        val active = linkedMapOf<String, Pair<Long, Long>>()

        for (connection in snapshot.connections) {
            val id = connection.id
            val metadata = connection.metadata
            val upload = connection.upload
            val download = connection.download
            val old = previous[id]
            val chain = connection.chains

            fun rate(value: Long, before: Long?) = if (elapsed > 0 && before != null && value >= before) {
                ((value - before).toDouble() * 1000 / elapsed).roundToLong()
            } else {
                0L
            }

            records[id] = ConnectionDetailState(
                uuid = id,
                network = metadata.network,
                inbound = metadata.type,
                uploadTotal = upload,
                downloadTotal = download,
                uploadSpeed = rate(upload, old?.first),
                downloadSpeed = rate(download, old?.second),
                startedAt = formatConnectionTime(parseClashTime(connection.start).toLong()),
                src = joinAddress(
                    metadata.sourceIP,
                    metadata.sourcePort,
                ),
                dst = joinAddress(
                    metadata.destinationIP,
                    metadata.destinationPort,
                ),
                host = metadata.host,
                matchedRule = listOf(connection.rule, connection.rulePayload)
                    .filter { it.isNotBlank() }
                    .joinToString(": "),
                outbound = chain.firstOrNull().orEmpty(),
                chain = chain.joinToString(" => "),
                processes = metadata.processPath.blankAsNull()?.let(::listOf),
            )
            active[id] = upload to download
        }

        for ((id, record) in records.toMap()) {
            if (id !in active && !record.isClosed) {
                records[id] = record.copy(
                    closedAt = formatConnectionTime(now),
                    uploadSpeed = 0,
                    downloadSpeed = 0,
                )
            }
        }

        val closed = records.filterValues { it.isClosed }.keys.toList()
        for (id in closed.take((closed.size - 1000).fastCoerceAtLeast(0))) {
            records.remove(id)
        }

        sampledAt = now
        previous = active
        return records.values.toList()
    }
}

@JsFun("() => Date.now()")
private external fun nowMillis(): Double

@JsFun("value => { const time = Date.parse(value); return Number.isFinite(time) ? time : 0; }")
private external fun parseClashTime(value: String): Double
