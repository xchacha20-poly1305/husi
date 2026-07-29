package fr.husi.bg.proto

import fr.husi.BuildConfig
import fr.husi.aidl.SpeedDisplayData
import fr.husi.bg.BaseService
import fr.husi.bg.SpeedStats
import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.ktx.Logs
import fr.husi.repository.resolveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ProxyInstance(profile: ProxyEntity, var service: BaseService.Interface? = null) :
    BoxInstance(profile) {

    var displayProfileName = profile.displayNameForService()

    var trafficLooper: TrafficLooper? = null

    /** Owns the traffic looper, cancelled in [close] so nothing outlives this instance. */
    private val looperScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun buildConfig() {
        super.buildConfig()
        Logs.d(config.config)
        if (DataStore.isExpert) Logs.d("trafficMap: " + config.trafficMap.toString())
    }

    override suspend fun init(isVPN: Boolean) {
        super.init(isVPN)
        pluginConfigs.forEach { (_, plugin) ->
            val (_, content) = plugin
            Logs.d(content)
        }
    }

    override fun launch() {
        super.launch() // start box
        looperScope.launch {
            val data = service?.data ?: return@launch
            trafficLooper = TrafficLooper(
                box = resolveRepository().boxService!!,
                config = config,
                scope = looperScope,
                onSpeedUpdate = { stats ->
                    val speed = stats.toSpeedDisplayData()
                    data.binder.notifySpeed(speed)
                    data.notification.apply {
                        if (canPostSpeed()) onSpeed(speed)
                    }
                },
            )
            trafficLooper?.start()
        }
    }

    override fun close() {
        super.close()
        runBlocking {
            trafficLooper?.stop()
            trafficLooper = null
        }
        looperScope.cancel()
    }
}

private fun SpeedStats.toSpeedDisplayData() = SpeedDisplayData(
    txRateProxy = txRateProxy,
    rxRateProxy = rxRateProxy,
    txRateDirect = txRateDirect,
    rxRateDirect = rxRateDirect,
    txTotal = txTotal,
    rxTotal = rxTotal,
)
