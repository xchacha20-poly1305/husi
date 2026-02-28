package fr.husi.bg

import fr.husi.aidl.SpeedDisplayData

interface ServiceNotifier {
    fun canPostSpeed(): Boolean = false

    suspend fun onTitle(title: String) {
    }

    suspend fun onSpeed(speed: SpeedDisplayData) {
    }

    suspend fun onWakeLock(acquired: Boolean) {
    }

    fun destroy() {
    }
}

object NoopServiceNotifier : ServiceNotifier
