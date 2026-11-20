package fr.husi.bg

import android.annotation.SuppressLint
import android.net.Network
import fr.husi.libcore.InterfaceUpdateListener
import fr.husi.repository.resolveAndroidRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.NetworkInterface
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("MissingPermission")
object DefaultNetworkMonitor {
    @Volatile
    var defaultNetwork: Network? = null

    @Volatile
    private var listener: InterfaceUpdateListener? = null
    private val access = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var retryJob: Job? = null
    private var refCount = 0

    suspend fun start() {
        access.withLock {
            if (refCount++ > 0) return
            AndroidDefaultNetworkListener.start(this) {
                defaultNetwork = it
                checkDefaultInterfaceUpdate(it)
            }
            defaultNetwork = resolveAndroidRepository().connectivity.activeNetwork
        }
    }

    suspend fun stop() {
        access.withLock {
            if (refCount == 0) return
            if (--refCount > 0) return
            retryJob?.cancel()
            retryJob = null
            AndroidDefaultNetworkListener.stop(this)
        }
    }

    suspend fun <T> withDefaultNetwork(block: suspend (Network) -> T): T {
        start()
        try {
            return block(require())
        } finally {
            stop()
        }
    }

    suspend fun require(): Network {
        val network = defaultNetwork
        if (network != null) {
            return network
        }
        return AndroidDefaultNetworkListener.get()
    }

    fun setListener(listener: InterfaceUpdateListener?) {
        this.listener = listener
        retryJob?.cancel()
        retryJob = null
        if (listener == null) return
        val network = defaultNetwork
        if (tryUpdateDefaultInterface(listener, network)) return
        retryJob = scope.launch {
            retryDefaultInterfaceUpdate(listener, network)
        }
    }

    private suspend fun checkDefaultInterfaceUpdate(newNetwork: Network?) {
        retryJob?.cancel()
        retryJob = null
        val listener = listener ?: return
        if (tryUpdateDefaultInterface(listener, newNetwork)) return
        retryDefaultInterfaceUpdate(listener, newNetwork)
    }

    private suspend fun retryDefaultInterfaceUpdate(
        listener: InterfaceUpdateListener,
        newNetwork: Network?,
    ) {
        for (attempt in 1 until 10) {
            delay(100.milliseconds)
            if (this.listener !== listener || defaultNetwork != newNetwork) return
            if (tryUpdateDefaultInterface(listener, newNetwork)) return
        }
    }

    private fun tryUpdateDefaultInterface(
        listener: InterfaceUpdateListener,
        newNetwork: Network?,
    ): Boolean {
        if (newNetwork == null) {
            listener.updateDefaultInterface("", -1)
            return true
        }
        val repo = resolveAndroidRepository()
        val linkProperties = repo.connectivity.getLinkProperties(newNetwork) ?: return false
        val interfaceName = linkProperties.interfaceName ?: return false
        val interfaceIndex = try {
            NetworkInterface.getByName(interfaceName).index
        } catch (_: Exception) {
            return false
        }
        listener.updateDefaultInterface(interfaceName, interfaceIndex)
        return true
    }
}
