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
import fr.husi.ui.dashboard.DashboardState
import fr.husi.ui.openconnect.RemoteOpenConnectActions
import fr.husi.ui.openvpn.RemoteOpenVPNActions
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

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

    private var session: Job? = null
    private var generation = 0
    val detector = BackendDetector(client, openClash)

    private companion object {
        val INITIAL_RETRY_DELAY = 1.seconds
        val MAX_RETRY_DELAY = 30.seconds
    }

    init {
        scope.launch {
            combine(serverStore.observe(), activeServerId.flow()) { servers, activeId ->
                servers to servers.firstOrNull { it.id == activeId }
            }
                .catch { failure ->
                    if (failure is CancellationException) throw failure
                    reportCriticalError("Remote server storage failed", failure)
                    initialized = true
                }
                .collect { (servers, server) ->
                    this@RemoteState.servers.clear()
                    this@RemoteState.servers.addAll(servers)
                    if (selected != server) {
                        if (server == null) {
                            stopSession()
                        } else {
                            startSession(server)
                        }
                    }
                    initialized = true
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

    suspend fun save(server: RemoteServer): RemoteServer = serverStore.save(server)

    suspend fun delete(server: RemoteServer) {
        serverStore.delete(server.id)
        if (activeServerId.get() == server.id) activeServerId.set(0L)
    }

    fun connect(server: RemoteServer) {
        scope.launch { activeServerId.set(server.id) }
    }

    fun disconnect() {
        scope.launch { activeServerId.set(0L) }
    }

    private fun stopSession() {
        session?.cancel()
        session = null
        selected = null
        resolvedBackend = null
        connecting = false
        error = ""
        clearConnection()
    }

    private fun clearConnection() {
        Logs.d("Remote session closed")
        generation++
        currentSession?.close() ?: client.close()
        currentSession = null
        connected = false
        openConnectActions.update(OpenConnectStatusUpdate())
        openVPNActions.update(OpenVPNStatusUpdate())
    }

    private fun startSession(server: RemoteServer) {
        stopSession()
        selected = server
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
                                ClashSession(server.url, server.secret, openClash(server.url, server.secret))
                            }

                            else ->
                                SingBoxSession(client) {
                                    if (currentSession === it) {
                                        openConnectActions.update(it.openConnect)
                                        openVPNActions.update(it.openVPN)
                                    }
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
                    opened.observe(speedInterval)
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
}
