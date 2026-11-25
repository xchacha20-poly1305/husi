package fr.husi.bg

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import fr.husi.repository.resolveAndroidRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

actual object DefaultNetworkListener {
    actual suspend fun start(key: Any, listener: suspend () -> Unit) {
        AndroidDefaultNetworkListener.start(key) {
            listener()
        }
    }

    actual suspend fun stop(key: Any) {
        AndroidDefaultNetworkListener.stop(key)
    }
}

internal object AndroidDefaultNetworkListener {
    private sealed interface CallbackEvent {
        class Available(val network: Network) : CallbackEvent
        class Update(val network: Network) : CallbackEvent
        class Lost(val network: Network) : CallbackEvent
        data object Fallback : CallbackEvent
    }

    private data class NetworkEvent(
        val network: Network?,
        val version: Long,
        val isFallback: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val access = Mutex()
    private val listeners = mutableMapOf<Any, Job>()

    @Volatile
    private var fallback = false

    private val networkEvents: SharedFlow<NetworkEvent> = flow {
        var currentNetwork: Network? = null
        var version = 0L
        rawNetworkEvents().collect { event ->
            when (event) {
                is CallbackEvent.Available -> {
                    currentNetwork = event.network
                    emit(NetworkEvent(currentNetwork, ++version))
                }

                is CallbackEvent.Update -> {
                    if (currentNetwork == event.network) {
                        emit(NetworkEvent(currentNetwork, ++version))
                    }
                }

                is CallbackEvent.Lost -> {
                    if (currentNetwork == event.network) {
                        currentNetwork = null
                        emit(NetworkEvent(null, ++version))
                    }
                }

                CallbackEvent.Fallback -> {
                    currentNetwork = null
                    emit(NetworkEvent(null, ++version, isFallback = true))
                }
            }
        }
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 0L,
            replayExpirationMillis = 0L,
        ),
        replay = 1,
    )

    private fun rawNetworkEvents() = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(CallbackEvent.Available(network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                // it's a good idea to refresh capabilities
                trySend(CallbackEvent.Update(network))
            }

            override fun onLost(network: Network) {
                trySend(CallbackEvent.Lost(network))
            }
        }
        val registered = register(callback)
        if (!registered) {
            trySend(CallbackEvent.Fallback)
        }
        awaitClose {
            if (registered) {
                unregister(callback)
            }
            fallback = false
        }
    }.buffer(Channel.UNLIMITED)

    suspend fun start(key: Any, listener: suspend (Network?) -> Unit) {
        access.withLock {
            listeners.remove(key)?.cancel()
            listeners[key] = scope.launch {
                networkEvents.withIndex().collect { (index, event) ->
                    if (event.isFallback || (index == 0 && event.network == null)) {
                        return@collect
                    }
                    listener(event.network)
                }
            }
        }
    }

    suspend fun get(): Network {
        if (fallback) {
            return resolveAndroidRepository().connectivity.activeNetwork
                ?: error("missing default network") // failed to listen, return current if available
        }
        check(access.withLock { listeners.isNotEmpty() }) {
            "Getting network without any listeners is not supported"
        }
        val event = networkEvents.first { it.isFallback || it.network != null }
        if (event.isFallback) {
            return resolveAndroidRepository().connectivity.activeNetwork
                ?: error("missing default network") // failed to listen, return current if available
        }
        return event.network!!
    }

    suspend fun stop(key: Any) {
        val job = access.withLock {
            listeners.remove(key)
        }
        job?.cancelAndJoin()
    }

    private val request = NetworkRequest.Builder().apply {
        addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    }.build()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Unfortunately registerDefaultNetworkCallback is going to return VPN interface since Android P DP1:
     * https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately, we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    private fun register(callback: ConnectivityManager.NetworkCallback): Boolean {
        when (Build.VERSION.SDK_INT) {
            in 31..Int.MAX_VALUE -> @RequiresApi(31) {
                resolveAndroidRepository().connectivity.registerBestMatchingNetworkCallback(
                    request,
                    callback,
                    mainHandler,
                )
            }

            in 28 until 31 -> @RequiresApi(28) {  // we want REQUEST here instead of LISTEN
                resolveAndroidRepository().connectivity.requestNetwork(request, callback, mainHandler)
            }

            in 26 until 28 -> @RequiresApi(26) {
                resolveAndroidRepository().connectivity.registerDefaultNetworkCallback(callback, mainHandler)
            }

            in 24 until 26 -> {
                resolveAndroidRepository().connectivity.registerDefaultNetworkCallback(callback)
            }

        }
        return true
    }

    private fun unregister(callback: ConnectivityManager.NetworkCallback) {
        runCatching {
            resolveAndroidRepository().connectivity.unregisterNetworkCallback(callback)
        }
    }
}
