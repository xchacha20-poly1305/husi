package fr.husi.bg.proto

import fr.husi.Key
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.bg.SpeedStats
import fr.husi.core.CoreClient
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.fmt.ConfigBuildResult
import fr.husi.fmt.TAG_DIRECT
import fr.husi.ktx.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class TrafficLooper(
    private val coreClient: CoreClient,
    private val config: ConfigBuildResult,
    private val scope: CoroutineScope,
    private val onSpeedUpdate: (suspend (SpeedStats) -> Unit)? = null,
) {

    private var job: Job? = null
    private val aggregator = OutboundTrafficAggregator()
    private val idMap = mutableMapOf<Long, TrafficUpdater.TrafficLooperData>() // id to 1 data
    private val tagMap = mutableMapOf<String, TrafficUpdater.TrafficLooperData>() // tag to 1 data
    private val selectedByGroup = mutableMapOf<String, String>()

    suspend fun stop() {
        job?.cancel()
        job = null
        if (!DataStore.profileTrafficStatistics) return
        updateDb()
        Logs.d("finally traffic post done")
    }

    fun start() {
        job = scope.launch { loop() }
    }

    fun updateSelectedTag(groupName: String, old: String, new: String) {
        val group = config.trafficMap[groupName] ?: return
        val oldID = config.tagToID[old]
        val newID = config.tagToID[new]
        for (entity in group) {
            when (entity.id) {
                oldID -> {
                    idMap[oldID]?.ignore = true
                }

                newID -> {
                    idMap[newID]?.ignore = false
                }
            }
        }
    }

    internal fun ignoreByEntityId(): Map<Long, Boolean> =
        idMap.mapValues { it.value.ignore }

    internal fun seedIdMapForTest(flags: Map<Long, Boolean>) {
        idMap.clear()
        for ((id, ignore) in flags) {
            idMap[id] = TrafficUpdater.TrafficLooperData(tag = "t-$id", ignore = ignore)
        }
    }

    private suspend fun loop() = coroutineScope {
        val speedInterval = DataStore.configurationStore
            .intFlow(Key.SPEED_INTERVAL, 1000)
            .stateIn(this, SharingStarted.Eagerly, 1000)
        val profileTrafficStatistics = DataStore.configurationStore
            .booleanFlow(Key.PROFILE_TRAFFIC_STATISTICS, true)
            .stateIn(this, SharingStarted.Eagerly, true)
        val persistEveryMs = 10_000L

        val itemBypass = TrafficUpdater.TrafficLooperData(tag = TAG_DIRECT)

        idMap.clear()
        idMap[-1] = itemBypass
        val mainID = config.tagToID[config.mainTag]
        config.trafficMap.forEach { (tag, entities) ->
            val isProxySet = entities.any { it.type == ProxyEntity.TYPE_PROXY_SET }
            for (ent in entities) {
                val item = TrafficUpdater.TrafficLooperData(
                    tag = tag,
                    rx = ent.rx,
                    tx = ent.tx,
                    rxBase = ent.rx,
                    txBase = ent.tx,
                    ignore = isProxySet && ent.id != mainID,
                )
                idMap[ent.id] = item
                tagMap[tag] = item
                Logs.d("traffic count $tag to ${ent.id}")
            }
        }
        val trafficUpdater = TrafficUpdater(
            aggregator = aggregator, items = idMap.values.toList(),
        )

        // Seed selected tags and track subsequent selection changes via groups stream.
        launch {
            coreClient.subscribeGroups().collect { groups ->
                for (group in groups.groupList) {
                    val previous = selectedByGroup.put(group.tag, group.selected)
                    if (previous != null && previous != group.selected) {
                        updateSelectedTag(group.tag, previous, group.selected)
                    } else if (previous == null && group.selected.isNotEmpty()) {
                        // Initial snapshot: un-ignore the actually selected member.
                        // Empty old is a no-op on the old side of updateSelectedTag
                        // (matches deleted InitializeProxySet OnGroupSelectedChange).
                        updateSelectedTag(group.tag, "", group.selected)
                    }
                }
            }
        }

        launch {
            speedInterval.collectLatest { intervalMs ->
                if (intervalMs <= 0) return@collectLatest
                coreClient.subscribeConnections(intervalMs.milliseconds).collect { events ->
                    aggregator.onEvents(events)
                }
            }
        }

        var lastPersist = System.currentTimeMillis()
        while (isActive) {
            val intervalMs = speedInterval.value.toLong().coerceAtLeast(0L)
            if (intervalMs <= 0L) {
                delay(200.milliseconds)
                continue
            }
            delay(intervalMs.milliseconds)

            trafficUpdater.updateAll()

            var mainTxRate = 0L
            var mainRxRate = 0L
            var mainTx = 0L
            var mainRx = 0L
            tagMap.forEach { (_, it) ->
                if (!it.ignore) {
                    mainTxRate += it.txRate
                    mainRxRate += it.rxRate
                }
                mainTx += it.tx - it.txBase
                mainRx += it.rx - it.rxBase
            }

            val speedStats = SpeedStats(
                txRateProxy = mainTxRate,
                rxRateProxy = mainRxRate,
                txRateDirect = itemBypass.txRate,
                rxRateDirect = itemBypass.rxRate,
                txTotal = mainTx,
                rxTotal = mainRx,
            )

            if (DataStore.serviceState == ServiceState.Connected) {
                BackendState.updateSpeed(speedStats)
                onSpeedUpdate?.invoke(speedStats)
            }

            if (profileTrafficStatistics.value) {
                val now = System.currentTimeMillis()
                if (now - lastPersist >= persistEveryMs) {
                    updateDb()
                    lastPersist = now
                }
            }
        }
    }

    private suspend fun updateDb() {
        config.trafficMap.forEach { (_, entities) ->
            for (entity in entities) {
                val item = idMap[entity.id] ?: return@forEach
                ProfileManager.updateTraffic(entity, item.tx, item.rx)
            }
        }
    }
}
