package fr.husi.bg

interface ServiceNotifier {
    fun canPostSpeed(): Boolean = false

    suspend fun onTitle(title: String) {
    }

    suspend fun onSpeed(speed: SpeedStats) {
    }

    suspend fun onWakeLock(acquired: Boolean) {
    }

    fun destroy() {
    }
}

object NoopServiceNotifier : ServiceNotifier
