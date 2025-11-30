package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.TAG_DIRECT
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrafficLooper(
    val data: BaseService.Data, private val scope: CoroutineScope,
) {

    private var job: Job? = null
    private val idMap = mutableMapOf<Long, TrafficUpdater.TrafficLooperData>() // id to 1 data
    private val tagMap = mutableMapOf<String, TrafficUpdater.TrafficLooperData>() // tag to 1 data

    suspend fun stop() {
        job?.cancel()
        if (!DataStore.profileTrafficStatistics) return
        updateDb()
        Logs.d("finally traffic post done")
    }

    fun start() {
        job = scope.launch { loop() }
    }

    fun updateSelectedTag(groupName: String, old: String, new: String) {
        val group = data.proxy?.config?.trafficMap?.get(groupName) ?: return
        val oldID = data.proxy?.config?.tagToID?.get(old)
        val newID = data.proxy?.config?.tagToID?.get(new)
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

    private suspend fun loop() {
        val speedInterval = DataStore.configurationStore
            .intFlow(Key.SPEED_INTERVAL)
            .map { it.toLong() }
            .stateIn(scope, SharingStarted.Eagerly, DataStore.speedInterval.toLong())
        val showDirectSpeed = DataStore.configurationStore
            .booleanFlow(Key.SHOW_DIRECT_SPEED)
            .stateIn(scope, SharingStarted.Eagerly, DataStore.showDirectSpeed)
        val profileTrafficStatistics = DataStore.configurationStore
            .booleanFlow(Key.PROFILE_TRAFFIC_STATISTICS)
            .stateIn(scope, SharingStarted.Eagerly, DataStore.profileTrafficStatistics)
        // update database / 10s
        val persistEveryMs = 10_000L
        // Calculate loop times (ticks) based on delay ms.
        fun persistTicksForDelay(delay: Long): Long {
            val effectiveDelay = delay.coerceAtLeast(1L)
            return ((persistEveryMs + effectiveDelay - 1) / effectiveDelay).coerceAtLeast(1L)
        }
        var delayMs = speedInterval.value
        var persistTicks = if (delayMs > 0) persistTicksForDelay(delayMs) else 1L
        var ticks = 0L

        var trafficUpdater: TrafficUpdater? = null
        var proxy: ProxyInstance?

        // for display
        val itemBypass = TrafficUpdater.TrafficLooperData(tag = TAG_DIRECT)

        while (scope.isActive) {
            var currentDelayMs = speedInterval.value
            if (currentDelayMs <= 0L) {
                delayMs = 0L
                ticks = 0
                // Wait until valid value
                currentDelayMs = speedInterval.filter { it > 0L }.first()
            }
            if (currentDelayMs != delayMs) {
                delayMs = currentDelayMs
                persistTicks = persistTicksForDelay(delayMs)
                ticks = 0
            }

            proxy = data.proxy
            if (proxy == null) {
                delay(delayMs)
                continue
            }

            if (trafficUpdater == null) {
                if (!proxy.isInitialized()) continue
                idMap.clear()
                idMap[-1] = itemBypass
                val tags = hashSetOf(proxy.config.mainTag, TAG_DIRECT)
                val mainID = proxy.config.tagToID[proxy.config.mainTag]
                proxy.config.trafficMap.forEach { (tag, entities) ->
                    tags.add(tag)
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
                trafficUpdater = TrafficUpdater(
                    box = proxy.box, items = idMap.values.toList()
                )
                proxy.box.initializeProxySet()
            }

            trafficUpdater.updateAll()
            if (!scope.isActive) return

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
            val speed = SpeedDisplayData(
                mainTxRate,
                mainRxRate,
                if (showDirectSpeed.value) itemBypass.txRate else 0L,
                if (showDirectSpeed.value) itemBypass.rxRate else 0L,
                mainTx,
                mainRx,
            )

            // broadcast (MainActivity)
            if (data.state == BaseService.State.Connected
                && data.binder.callbackIdMap.containsValue(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
            ) {
                data.binder.broadcast { b ->
                    if (data.binder.callbackIdMap[b] == SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND) {
                        b.cbSpeedUpdate(speed)
                    }
                }
            }

            // ServiceNotification
            data.notification?.apply {
                if (listenPostSpeed) postNotificationSpeedUpdate(speed)
            }

            if (profileTrafficStatistics.value) {
                if (++ticks >= persistTicks) {
                    updateDb()
                    ticks = 0
                }
            } else {
                ticks = 0
            }

            delay(delayMs)
        }
    }

    private suspend fun updateDb() {
        data.proxy?.config?.trafficMap?.forEach { (_, entities) ->
            for (entity in entities) {
                val item = idMap[entity.id] ?: return@forEach
                ProfileManager.updateTraffic(entity, item.tx, item.rx)
            }
        }
    }
}
