package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.aidl.DashboardStatus
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.toConnectionList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libcore.Libcore

class DashboardStatusLooper(
    val data: BaseService.Data, private val scope: CoroutineScope,
) {

    private var job: Job? = null

    fun start() {
        job = scope.launch { loop() }
    }

    private suspend fun loop() {
        val interval = DataStore.speedInterval.let {
            if (it <= 0) 1000L else it.toLong()
        }

        while (scope.isActive) {
            try {
                data.binder.broadcast { work ->
                    work.dashboardStatusUpdate(
                        DashboardStatus(
                            data.proxy!!.box.trackerInfos.toConnectionList(),
                            Libcore.getMemory(),
                            Libcore.getGoroutines(),
                            false,
                        )
                    )
                }
            } catch (e: Exception) {
                Logs.e(e)
            }

            delay(interval)
        }
    }

    fun stop() {
        job?.cancel()
        runOnDefaultDispatcher {
            runCatching {
                data.binder.broadcast { work ->
                    work.dashboardStatusUpdate(
                        DashboardStatus(emptyList(), 0, 0, true)
                    )
                }
            }
        }
    }
}
