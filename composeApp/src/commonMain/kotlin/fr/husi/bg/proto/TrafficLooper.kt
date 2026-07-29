package fr.husi.bg.proto

import fr.husi.Key
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.bg.SpeedStats
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyEntity
import fr.husi.fmt.ConfigBuildResult
import fr.husi.fmt.TAG_DIRECT
import fr.husi.ktx.Logs
import fr.husi.libcore.ConnectionEvent
import fr.husi.libcore.ConnectionSubscription
import fr.husi.libcore.Libcore
import fr.husi.libcore.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrafficLooper(
    private val box: Service,
    private val config: ConfigBuildResult,
    private val scope: CoroutineScope,
    private val onSpeedUpdate: (suspend (SpeedStats) -> Unit)? = null,
) {

    private var job: Job? = null
    private var subscription: ConnectionSubscription? = null
    private val aggregator = OutboundTrafficAggregator()
    private val idMap = mutableMapOf<Long, TrafficUpdater.TrafficLooperData>() // id to 1 data
    private val tagMap = mutableMapOf<String, TrafficUpdater.TrafficLooperData>() // tag to 1 data

    private val ticks = Channel<Unit>(Channel.CONFLATED)

    suspend fun stop() {
        job?.cancel()
        subscription?.let { sub -> runCatching { sub.close() } }
        subscription = null
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

    private fun onConnectionEvent(event: ConnectionEvent) {
        if (event.type == Libcore.ConnectionEventTick) {
            ticks.trySend(Unit)
        } else {
            aggregator.onEvent(event)
        }
    }

    private suspend fun loop() = coroutineScope {
        // Share into this scope rather than the outer one, so that cancelling [job]
        // in [stop] also tears down these eager collectors.
        val speedInterval = DataStore.configurationStore
            .intFlow(Key.SPEED_INTERVAL, 1000)
            .stateIn(this, SharingStarted.Eagerly, 1000)
        val showDirectSpeed = DataStore.configurationStore
            .booleanFlow(Key.SHOW_DIRECT_SPEED, true)
            .stateIn(this, SharingStarted.Eagerly, true)
        val profileTrafficStatistics = DataStore.configurationStore
            .booleanFlow(Key.PROFILE_TRAFFIC_STATISTICS, true)
            .stateIn(this, SharingStarted.Eagerly, true)
        // update database / 10s
        val persistEveryMs = 10_000L

        // for display
        val itemBypass = TrafficUpdater.TrafficLooperData(tag = TAG_DIRECT)

        // initialize
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
        box.initializeProxySet()
        subscription = runCatching { box.subscribeConnections(::onConnectionEvent, speedInterval.value) }
            .onFailure { Logs.w("subscribe connections", it) }
            .getOrNull()

        launch {
            speedInterval.collect { interval ->
                subscription?.updateInterval(interval)
            }
        }

        var lastPersist = System.currentTimeMillis()
        for (tick in ticks) {
            if (speedInterval.value <= 0) continue

            trafficUpdater.updateAll()

            // add all non-bypass to "main"
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

            // speed
            val speedStats = SpeedStats(
                txRateProxy = mainTxRate,
                rxRateProxy = mainRxRate,
                txRateDirect = if (showDirectSpeed.value) itemBypass.txRate else 0L,
                rxRateDirect = if (showDirectSpeed.value) itemBypass.rxRate else 0L,
                txTotal = mainTx,
                rxTotal = mainRx,
            )

            // Update shared speed state
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
