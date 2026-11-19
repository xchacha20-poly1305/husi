package fr.husi.bg

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.NetworkInterface

actual object DefaultNetworkListener {
    private val listeners = mutableMapOf<Any, suspend () -> Unit>()
    private val access = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var interfaceSnapshot = emptySet<String>()

    actual suspend fun start(key: Any, listener: suspend () -> Unit) {
        access.withLock {
            listeners[key] = listener
            if (monitorJob == null) {
                monitorJob = scope.launch {
                    monitorInterfaces()
                }
            }
        }
    }

    actual suspend fun stop(key: Any) {
        access.withLock {
            listeners.remove(key)
            if (listeners.isNotEmpty()) return
            monitorJob?.cancel()
            monitorJob = null
            interfaceSnapshot = emptySet()
        }
    }

    private suspend fun monitorInterfaces() {
        val initialSnapshot = snapshotInterfaces()
        access.withLock {
            if (listeners.isNotEmpty()) {
                interfaceSnapshot = initialSnapshot
            }
        }
        while (currentCoroutineContext().isActive) {
            delay(1000)
            val currentSnapshot = snapshotInterfaces()
            val callbacks = access.withLock {
                if (listeners.isEmpty() || currentSnapshot == interfaceSnapshot) {
                    return@withLock emptyList()
                }
                interfaceSnapshot = currentSnapshot
                listeners.values.toList()
            }
            for (callback in callbacks) {
                runCatching {
                    callback()
                }
            }
        }
    }

    private fun snapshotInterfaces(): Set<String> {
        val interfaces = runCatching {
            NetworkInterface.getNetworkInterfaces()
        }.getOrNull() ?: return emptySet()
        val snapshot = LinkedHashSet<String>()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val name = networkInterface.name ?: continue
            val active = runCatching {
                networkInterface.isUp && !networkInterface.isLoopback
            }.getOrDefault(false)
            if (!active) continue
            val addresses = networkInterface.interfaceAddresses.mapNotNull { address ->
                val hostAddress = address.address?.hostAddress ?: return@mapNotNull null
                "$hostAddress/${address.networkPrefixLength}"
            }.sorted()
            snapshot += "$name:${addresses.joinToString(",")}"
        }
        return snapshot
    }
}
