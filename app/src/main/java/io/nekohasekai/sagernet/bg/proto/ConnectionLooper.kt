package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.aidl.ConnectionList
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.toConnectionList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ConnectionLooper(
    val data: BaseService.Data, private val scope: CoroutineScope,
) {

    private var job: Job? = null

    fun start() {
        job = scope.launch { loop() }
    }

    private suspend fun loop() {
        val interval = DataStore.speedInterval.toLong()

        while (scope.isActive) {
            try {
                data.binder.broadcast { work ->
                    work.connectionUpdate(ConnectionList(data.proxy!!.box.trackerInfos.toConnectionList()))
                }
            } catch (e: Exception) {
                Logs.e(e)
            }

            delay(interval)
        }
    }

    fun stop() {
        job?.cancel()
    }
}