package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.fmt.TAG_DIRECT
import io.nekohasekai.sagernet.fmt.TAG_PROXY
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrafficLooper(
    val data: BaseService.Data, private val sc: CoroutineScope,
) {

    private var job: Job? = null
    private val idMap = mutableMapOf<Long, TrafficUpdater.TrafficLooperData>() // id to 1 data
    private val tagMap = mutableMapOf<String, TrafficUpdater.TrafficLooperData>() // tag to 1 data

    suspend fun stop() {
        job?.cancel()
        // finally traffic post
        if (!DataStore.profileTrafficStatistics) return
        val traffic = mutableMapOf<Long, TrafficData>()
        data.proxy?.config?.trafficMap?.forEach { (_, entities) ->
            for (ent in entities) {
                val item = idMap[ent.id] ?: return@forEach
                ProfileManager.updateTraffic(ent, item.tx, item.rx) // update DB
                traffic[ent.id] = TrafficData(
                    id = ent.id,
                    rx = ent.rx,
                    tx = ent.tx,
                )
            }
        }
        Logs.d("finally traffic post done")
    }

    fun start() {
        job = sc.launch { loop() }
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
        val delayMs = DataStore.speedInterval.toLong()
        if (delayMs == 0L) return
        val showDirectSpeed = DataStore.showDirectSpeed
        val profileTrafficStatistics = DataStore.profileTrafficStatistics

        var trafficUpdater: TrafficUpdater? = null
        var proxy: ProxyInstance?

        // for display
        val itemBypass = TrafficUpdater.TrafficLooperData(tag = TAG_DIRECT)

        while (sc.isActive) {
            proxy = data.proxy
            if (proxy == null) {
                delay(delayMs)
                continue
            }

            if (trafficUpdater == null) {
                if (!proxy.isInitialized()) continue
                idMap.clear()
                idMap[-1] = itemBypass
                val tags = hashSetOf(TAG_PROXY, TAG_DIRECT)
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
                            ignore = isProxySet && ent.id != proxy.config.main,
                        )
                        idMap[ent.id] = item
                        tagMap[tag] = item
                        Logs.d("traffic count $tag to ${ent.id}")
                    }
                }
                trafficUpdater = TrafficUpdater(
                    box = proxy.box, items = idMap.values.toList()
                )
            }

            trafficUpdater.updateAll()
            if (!sc.isActive) return

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
                if (showDirectSpeed) itemBypass.txRate else 0L,
                if (showDirectSpeed) itemBypass.rxRate else 0L,
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
                        if (profileTrafficStatistics) {
                            idMap.forEach { (id, item) ->
                                b.cbTrafficUpdate(
                                    TrafficData(id = id, rx = item.rx, tx = item.tx) // display
                                )
                            }
                        }

                    }
                }
            }

            // ServiceNotification
            data.notification?.apply {
                if (listenPostSpeed) postNotificationSpeedUpdate(speed)
            }

            delay(delayMs)
        }
    }
}
