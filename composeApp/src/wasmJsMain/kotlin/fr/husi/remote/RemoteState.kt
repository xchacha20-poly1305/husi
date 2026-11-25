package fr.husi.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.husi.database.DataStore
import fr.husi.database.RemoteBackend
import fr.husi.database.RemoteServer
import fr.husi.database.RemoteServerStore
import fr.husi.database.preference.PreferenceProxy
import fr.husi.ktx.Logs
import fr.husi.proto.daemon.OpenConnectStatusUpdate
import fr.husi.proto.daemon.OpenVPNStatusUpdate
import fr.husi.proto.daemon.StartedServiceClient
import fr.husi.proto.daemon.TailscaleStatusUpdate
import fr.husi.tailscale.TailscaleEndpointState
import fr.husi.tailscale.toTailscaleEndpoints
import fr.husi.ui.dashboard.DashboardState
import fr.husi.ui.openconnect.RemoteOpenConnectActions
import fr.husi.ui.openvpn.RemoteOpenVPNActions
import fr.husi.vpn.OpenConnectEndpointState
import fr.husi.vpn.OpenVPNEndpointState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.seconds

internal class RemoteState(
    private val scope: CoroutineScope,
    val client: RemoteClient,
    private val serverStore: RemoteServerStore,
    private val speedInterval: Flow<Int> = DataStore.speedInterval.flow(),
    private val activeServerId: PreferenceProxy<Long> = DataStore.activeRemoteServerId,
    private val openClash: (String, String) -> ClashApi = ::ClashBridge,
) {
    val openConnectActions = RemoteOpenConnectActions(this)
    val openVPNActions = RemoteOpenVPNActions(this)
    val tailscaleEndpoints: StateFlow<List<TailscaleEndpointState>>
        field = MutableStateFlow(emptyList())
    val activeNativeServer: StateFlow<RemoteServer?>
        field = MutableStateFlow(null)
    val liveTailscaleEndpoints: StateFlow<List<TailscaleEndpointState>?>
        field = MutableStateFlow(null)
    val liveOpenConnectEndpoints: StateFlow<List<OpenConnectEndpointState>?>
        field = MutableStateFlow(null)
    val liveOpenVPNEndpoints: StateFlow<List<OpenVPNEndpointState>?>
        field = MutableStateFlow(null)
    val servers = mutableStateListOf<RemoteServer>()
    var initialized by mutableStateOf(false)
        private set

    var terminalReady by mutableStateOf(false)
    var selected by mutableStateOf<RemoteServer?>(null)
        private set

    var connected by mutableStateOf(false)
        private set

    var connecting by mutableStateOf(false)
        private set

    var currentSession by mutableStateOf<RemoteSession?>(null)
        private set

    private var resolvedBackend by mutableStateOf<RemoteBackend?>(null)
    val backend
        get() = currentSession?.backend ?: resolvedBackend ?: selected?.backend

    val isClash
        get() = backend == RemoteBackend.CLASH

    val nativeSession
        get() = currentSession as? SingBoxSession

    val coreVersion
        get() = nativeSession?.coreVersion

    val version
        get() = currentSession?.version.orEmpty()

    val startedAt
        get() = nativeSession?.startedAt

    val dashboard
        get() = currentSession?.dashboard ?: DashboardState(isRemote = true)

    val logs
        get() = currentSession?.logs ?: emptyLogs

    private val emptyLogs = mutableStateListOf<RemoteLogEntry>()
    val tailscale
        get() = nativeSession?.tailscale ?: TailscaleStatusUpdate()

    /** Only failures that prevent connecting or preserving user data belong here. */
    var error by mutableStateOf("")
        private set

    fun reportCriticalError(context: String, failure: Throwable) {
        Logs.e(context, failure)
        error = failure.message ?: failure.toString()
    }

    private sealed interface ReplacementState {
        data object Idle : ReplacementState
        data object InProgress : ReplacementState
        data class Expected(val activeId: Long) : ReplacementState
    }

    private var session: Job? = null
    private var generation = 0
    private val replacementState = MutableStateFlow<ReplacementState>(ReplacementState.Idle)
    private val subscriptionCounts = mutableMapOf<RemoteSubscription, Int>()
    private val activeSubscriptions = MutableStateFlow<Set<RemoteSubscription>>(emptySet())
    val detector = BackendDetector(client, openClash)

    private companion object {
        val INITIAL_RETRY_DELAY = 1.seconds
        val MAX_RETRY_DELAY = 30.seconds
    }

    init {
        scope.launch {
            combine(serverStore.observe(), activeServerId.flow(), replacementState) { servers, activeId, replacement ->
                Triple(servers, activeId, replacement)
            }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    reportCriticalError("Remote server storage failed", failure)
                    initialized = true
                }
                .collect { (servers, activeId, replacement) ->
                    when (replacement) {
                        ReplacementState.InProgress -> return@collect
                        is ReplacementState.Expected -> {
                            if (activeId != replacement.activeId) {
                                return@collect
                            }
                            replacementState.value = ReplacementState.Idle
                        }

                        ReplacementState.Idle -> Unit
                    }
                    applyServerList(servers, servers.firstOrNull { it.id == activeId })
                    initialized = true
                }
        }
    }

    private fun applyServerList(servers: List<RemoteServer>, server: RemoteServer?) {
        this.servers.clear()
        this.servers.addAll(servers)
        if (selected != server) {
            if (server == null) {
                stopSession()
            } else {
                startSession(server)
            }
        }
    }

    fun close() {
        stopSession()
        scope.cancel()
    }

    fun logWarning(resource: StringResource) {
        scope.launch { Logs.w(getString(resource)) }
    }

    suspend fun listServers(): List<RemoteServer> = serverStore.observe().first()

    suspend fun save(server: RemoteServer): RemoteServer = serverStore.save(server)

    suspend fun replaceServers(servers: List<RemoteServer>): List<RemoteServer> {
        val previous = selected
        replacementState.value = ReplacementState.InProgress
        return try {
            val replaced = serverStore.replaceAll(servers)
            val next = if (previous != null) {
                replaced.firstOrNull {
                    it.url == previous.url
                        && it.secret == previous.secret
                        && it.backend == previous.backend
                }
                    ?: replaced.firstOrNull { it.id == previous.id }
                    ?: replaced.firstOrNull()
            } else {
                replaced.firstOrNull()
            }
            val nextId = next?.id ?: 0L
            activeServerId.set(nextId)
            replacementState.value = ReplacementState.Expected(nextId)
            applyServerList(replaced, next)
            replaced
        } finally {
            if (replacementState.value === ReplacementState.InProgress) {
                replacementState.value = ReplacementState.Idle
            }
        }
    }

    suspend fun delete(server: RemoteServer) {
        serverStore.delete(server.id)
        if (activeServerId.get() == server.id) activeServerId.set(0L)
    }

    fun connect(server: RemoteServer) {
        scope.launch {
            replacementState.value = ReplacementState.Idle
            activeServerId.set(server.id)
        }
    }

    fun disconnect() {
        scope.launch {
            replacementState.value = ReplacementState.Idle
            activeServerId.set(0L)
        }
    }

    fun retainSubscription(subscription: RemoteSubscription): () -> Unit {
        val current = subscriptionCounts[subscription] ?: 0
        subscriptionCounts[subscription] = current + 1
        if (current == 0) {
            activeSubscriptions.value = subscriptionCounts.keys.toSet()
        }
        var released = false
        return {
            if (!released) {
                released = true
                val remaining = (subscriptionCounts[subscription] ?: 0) - 1
                if (remaining <= 0) {
                    subscriptionCounts.remove(subscription)
                    activeSubscriptions.value = subscriptionCounts.keys.toSet()
                } else {
                    subscriptionCounts[subscription] = remaining
                }
            }
        }
    }

    private fun stopSession(preserveServerContext: Boolean = false) {
        session?.cancel()
        session = null
        selected = null
        resolvedBackend = null
        connecting = false
        error = ""
        clearConnection()
        if (!preserveServerContext) {
            resetServerContext()
            activeNativeServer.value = null
        }
    }

    private fun clearConnection() {
        Logs.d("Remote session closed")
        generation++
        currentSession?.close() ?: client.close()
        currentSession = null
        connected = false
        tailscaleEndpoints.value = emptyList()
        openConnectActions.update(OpenConnectStatusUpdate())
        openVPNActions.update(OpenVPNStatusUpdate())
    }

    private fun resetServerContext() {
        openConnectActions.clearDismissed()
        openVPNActions.clearDismissed()
        liveTailscaleEndpoints.value = null
        liveOpenConnectEndpoints.value = null
        liveOpenVPNEndpoints.value = null
    }

    private fun startSession(server: RemoteServer) {
        val sameEndpoint = selected?.sameEndpointAs(server) == true
        stopSession(preserveServerContext = sameEndpoint)
        selected = server
        activeNativeServer.value = if (server.backend == RemoteBackend.CLASH) null else server
        connecting = true
        error = ""
        session = scope.launch {
            var retryDelay = INITIAL_RETRY_DELAY
            while (isActive) {
                try {
                    val backend =
                        if (server.backend == RemoteBackend.AUTO) {
                            detector.test(server.url, server.secret, server.backend).backend
                        } else {
                            server.backend
                        }
                    ensureActive()
                    Logs.i("Remote backend resolved: $backend")
                    resolvedBackend = backend
                    val opened =
                        when (backend) {
                            RemoteBackend.CLASH -> {
                                resetServerContext()
                                activeNativeServer.value = null
                                ClashSession(server.url, server.secret, openClash(server.url, server.secret))
                            }

                            else -> {
                                activeNativeServer.value = server
                                SingBoxSession(
                                    client = client,
                                    onTailscaleUpdate = { session, update ->
                                        if (currentSession === session) {
                                            val mapped = update.toTailscaleEndpoints()
                                            tailscaleEndpoints.value = mapped
                                            liveTailscaleEndpoints.value = mapped
                                        }
                                    },
                                    onOpenConnectUpdate = { session, update ->
                                        if (currentSession === session) {
                                            liveOpenConnectEndpoints.value = openConnectActions.update(update)
                                        }
                                    },
                                    onOpenVPNUpdate = { session, update ->
                                        if (currentSession === session) {
                                            liveOpenVPNEndpoints.value = openVPNActions.update(update)
                                        }
                                    },
                                )
                            }
                        }
                    currentSession = opened
                    opened.connect(server.url, server.secret)
                    ensureActive()
                    Logs.i("Remote session connected: ${opened.backend}, version=${opened.version}")
                    connected = true
                    connecting = false
                    error = ""
                    retryDelay = INITIAL_RETRY_DELAY
                    opened.observe(speedInterval, activeSubscriptions)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    ensureActive()
                    clearConnection()
                    connecting = true
                    Logs.w("Remote connection failed; retrying in $retryDelay", failure)
                    error = failure.message.orEmpty()
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
                }
            }
        }
    }

    fun action(action: suspend RemoteSession.() -> Unit) {
        val opened = currentSession ?: return
        val current = generation
        scope.launch {
            if (generation != current) return@launch
            try {
                opened.action()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (generation == current) {
                    Logs.w("Remote action failed", failure)
                }
            }
        }
    }

    fun call(action: suspend StartedServiceClient.() -> Unit) {
        val current = generation
        scope.launch {
            if (generation != current) return@launch
            try {
                check(nativeSession != null) { "This operation requires sing-box RPC" }
                client.service.action()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (generation == current) Logs.w("Remote RPC action failed", failure)
            }
        }
    }

    /** Auth dialogs show failures inline instead of setting the session error. */
    suspend fun perform(action: suspend StartedServiceClient.() -> Unit): String? =
        try {
            check(nativeSession != null) { "This operation requires sing-box RPC" }
            client.service.action()
            null
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            Logs.w("Remote authentication action failed", failure)
            failure.message ?: failure.toString()
        }

    private fun RemoteServer.sameEndpointAs(other: RemoteServer): Boolean =
        id == other.id && url == other.url && secret == other.secret && backend == other.backend
}
