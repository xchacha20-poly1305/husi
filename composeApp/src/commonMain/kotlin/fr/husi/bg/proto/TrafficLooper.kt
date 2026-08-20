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

    /** Lifetime totals of one profile, seeded from what the database already holds. */
    private class ProfileTraffic(val entity: ProxyEntity) {
        var tx = entity.tx
        var rx = entity.rx
    }

    private var job: Job? = null
    private val aggregator = OutboundTrafficAggregator(config.trafficGraph)
    private val profiles = config.trafficProfiles.associate { it.id to ProfileTraffic(it) }

    /** Proxied bytes since this service started, for the session counter in the UI. */
    private var sessionTx = 0L
    private var sessionRx = 0L

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

    private suspend fun loop() = coroutineScope {
        val speedInterval = DataStore.configurationStore
            .intFlow(Key.SPEED_INTERVAL, 1000)
            .stateIn(this, SharingStarted.Eagerly, 1000)
        val profileTrafficStatistics = DataStore.configurationStore
            .booleanFlow(Key.PROFILE_TRAFFIC_STATISTICS, true)
            .stateIn(this, SharingStarted.Eagerly, true)
        val persistEveryMs = 10_000L

        launch {
            coreClient.subscribeGroups().collect { groups ->
                for (group in groups.groupList) {
                    aggregator.updateSelection(group.tag, group.selected)
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
        var lastDrain = System.currentTimeMillis()
        while (isActive) {
            val intervalMs = speedInterval.value.toLong().coerceAtLeast(0L)
            if (intervalMs <= 0L) {
                delay(200.milliseconds)
                continue
            }
            delay(intervalMs.milliseconds)

            val now = System.currentTimeMillis()
            val elapsedMs = (now - lastDrain).coerceAtLeast(1L)
            lastDrain = now
            val speedStats = drain(elapsedMs)

            if (DataStore.serviceState == ServiceState.Connected) {
                BackendState.updateSpeed(speedStats)
                onSpeedUpdate?.invoke(speedStats)
            }

            if (profileTrafficStatistics.value && now - lastPersist >= persistEveryMs) {
                updateDb()
                lastPersist = now
            }
        }
    }

    private fun drain(elapsedMs: Long): SpeedStats {
        val snapshot = aggregator.drain()
        for ((id, delta) in snapshot.byProfile) {
            val profile = profiles[id] ?: continue
            profile.tx += delta.upload
            profile.rx += delta.download
        }
        sessionTx += snapshot.proxied.upload
        sessionRx += snapshot.proxied.download

        fun bytesPerSecond(bytes: Long) = bytes * 1000 / elapsedMs

        return SpeedStats(
            txRateProxy = bytesPerSecond(snapshot.proxied.upload),
            rxRateProxy = bytesPerSecond(snapshot.proxied.download),
            txRateDirect = bytesPerSecond(snapshot.bypassed.upload),
            rxRateDirect = bytesPerSecond(snapshot.bypassed.download),
            txTotal = sessionTx,
            rxTotal = sessionRx,
        )
    }

    private suspend fun updateDb() {
        for (profile in profiles.values) {
            ProfileManager.updateTraffic(profile.entity, profile.tx, profile.rx)
        }
    }
}
