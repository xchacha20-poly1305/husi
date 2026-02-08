package io.nekohasekai.sagernet.bg

import android.net.Network
import libcore.InterfaceUpdateListener
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.NetworkInterface

object DefaultNetworkMonitor {
    var defaultNetwork: Network? = null
    private var listener: InterfaceUpdateListener? = null
    private val access = Mutex()
    private var refCount = 0

    suspend fun start() {
        access.withLock {
            if (refCount++ > 0) return
            DefaultNetworkListener.start(this) {
                defaultNetwork = it
                checkDefaultInterfaceUpdate(it)
            }
            defaultNetwork = repo.connectivity.activeNetwork
        }
    }

    suspend fun stop() {
        access.withLock {
            if (refCount == 0) return
            if (--refCount > 0) return
            DefaultNetworkListener.stop(this)
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
        return DefaultNetworkListener.get()
    }

    fun setListener(listener: InterfaceUpdateListener?) {
        this.listener = listener
        checkDefaultInterfaceUpdate(defaultNetwork)
    }

    private fun checkDefaultInterfaceUpdate(newNetwork: Network?) {
        val listener = listener ?: return
        if (newNetwork != null) {
            val interfaceName =
                repo.connectivity.getLinkProperties(newNetwork)?.interfaceName
            for (times in 0 until 10) {
                var interfaceIndex: Int
                try {
                    interfaceIndex = NetworkInterface.getByName(interfaceName).index
                } catch (_: Exception) {
                    Thread.sleep(100)
                    continue
                }
                listener.updateDefaultInterface(interfaceName, interfaceIndex)
            }
        } else {
            listener.updateDefaultInterface("", -1)
        }
    }
}
