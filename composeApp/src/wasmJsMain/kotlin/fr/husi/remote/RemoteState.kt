package fr.husi.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastCoerceAtLeast
import fr.husi.database.DataStore
import fr.husi.database.RemoteServer
import fr.husi.database.RemoteServerEntity
import fr.husi.database.preference.PreferenceProxy
import fr.husi.ui.openconnect.RemoteOpenConnectActions
import fr.husi.ui.openvpn.RemoteOpenVPNActions
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

internal class RemoteState(
    private val scope: CoroutineScope,
    val client: RemoteClient,
    private val serverDao: RemoteServerEntity.Dao,
    private val speedInterval: Flow<Int> = DataStore.speedInterval.flow(),
    private val activeServerId: PreferenceProxy<Long> = DataStore.activeRemoteServerId,
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
    var coreVersion by mutableStateOf<JsonObject?>(null)
        private set
    val version: String get() = coreVersion?.text("version").orEmpty()
    var error by mutableStateOf("")
    var traffic by mutableStateOf(RemoteTraffic())
    val history = mutableStateListOf<Float>()
    val groups = mutableStateListOf<JsonObject>()
    val connections = mutableStateMapOf<String, JsonObject>()
    val logs = mutableStateListOf<JsonObject>()
    var tailscale by mutableStateOf(JsonObject(emptyMap()))
    var openConnect by mutableStateOf(JsonObject(emptyMap()))
    var openVPN by mutableStateOf(JsonObject(emptyMap()))
    var clashMode by mutableStateOf(JsonObject(emptyMap()))
    var serviceStatus by mutableStateOf("")
    private var session: Job? = null
    private var generation = 0

    var startedAt by mutableStateOf<Long?>(null)
        private set

    private companion object {
        val INITIAL_RETRY_DELAY = 1.seconds
        val MAX_RETRY_DELAY = 30.seconds
        val HEARTBEAT_INTERVAL = 5.seconds
    }

    init {
        scope.launch {
            combine(serverDao.list(), activeServerId.flow()) { entities, activeId ->
                val servers = entities.map { it.toModel() }
                servers to servers.firstOrNull { it.id == activeId }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                error = failure.message.orEmpty()
                initialized = true
            }.collect { (servers, server) ->
                this@RemoteState.servers.clear()
                this@RemoteState.servers.addAll(servers)
                if (selected != server) {
                    if (server == null) stopSession() else startSession(server)
                }
                initialized = true
            }
        }
    }

    fun close() {
        stopSession()
        scope.cancel()
    }

    fun showError(resource: StringResource) {
        scope.launch { error = getString(resource) }
    }

    suspend fun save(server: RemoteServer): RemoteServer {
        val entity = server.toEntity()
        val id = serverDao.upsert(entity)
        return entity.toModel().copy(id = id)
    }

    suspend fun delete(server: RemoteServer) {
        if (selected?.id == server.id) activeServerId.set(0L)
        serverDao.delete(server.id)
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
        connecting = false
        error = ""
        clearConnection()
    }

    private fun clearConnection() {
        generation++
        client.close()
        startedAt = null
        connected = false
        coreVersion = null
        groups.clear()
        connections.clear()
        logs.clear()
        history.clear()
        traffic = RemoteTraffic()
        tailscale = JsonObject(emptyMap())
        openConnect = JsonObject(emptyMap())
        openVPN = JsonObject(emptyMap())
        openConnectActions.update(JsonObject(emptyMap()))
        openVPNActions.update(JsonObject(emptyMap()))
        clashMode = JsonObject(emptyMap())
        serviceStatus = ""
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
                    val response = client.connect(server.url, server.secret)
                    ensureActive()
                    coreVersion = response
                    connected = true
                    connecting = false
                    error = ""
                    retryDelay = INITIAL_RETRY_DELAY
                    observeSession()
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    ensureActive()
                    clearConnection()
                    connecting = true
                    error = failure.message.orEmpty()
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
                }
            }
        }
    }

    private suspend fun observeSession() = supervisorScope {
        try {
            subscribe(this, "SubscribeGroups") {
                groups.clear()
                groups.addAll(it.items("group"))
            }
            launch {
                speedInterval.collectLatest { interval ->
                    coroutineScope {
                        if (interval <= 0) return@coroutineScope
                        val request = request("interval" to interval.milliseconds.inWholeNanoseconds.toString())
                        subscribe(this, "SubscribeStatus", request) {
                            traffic = RemoteTraffic.fromJson(it)
                            history.add((traffic.upload + traffic.download).toFloat())
                            if (history.size > 60) {
                                history.removeAt(0)
                            }
                        }
                        subscribe(this, "SubscribeConnections", request) { event ->
                            applyConnectionEvents(connections, event, keepClosed = true)
                            val closed = connections.entries.filter { it.value.number("closedAt") > 0 }
                            for (entry in closed.take((closed.size - 1000).fastCoerceAtLeast(0))) {
                                connections.remove(entry.key)
                            }
                        }
                    }
                }
            }
            subscribe(this, "SubscribeLog") { update ->
                if (update.flag("reset")) logs.clear()
                logs.addAll(update.items("messages"))
                while (logs.size > 2000) logs.removeAt(0)
            }
            subscribe(this, "SubscribeTailscaleStatus") { tailscale = it }
            subscribe(this, "SubscribeOpenConnectStatus") {
                openConnect = it
                openConnectActions.update(it)
            }
            subscribe(this, "SubscribeOpenVPNStatus") {
                openVPN = it
                openVPNActions.update(it)
            }
            subscribe(this, "SubscribeClashMode") {
                clashMode =
                    JsonObject(clashMode + ("currentMode" to JsonPrimitive(it.text("mode"))))
            }
            subscribe(this, "SubscribeServiceStatus") { serviceStatus = it.text("status") }
            launch {
                runCatching { client.call("GetClashModeStatus") }.onSuccess { clashMode = it }
            }
            while (isActive) {
                val remoteStartedAt = try {
                    client.call("GetStartedAt").number("startedAt").takeIf { it > 0L }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    null
                }
                ensureActive()
                startedAt = remoteStartedAt
                delay(HEARTBEAT_INTERVAL)
                client.call("GetVersion")
            }
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    private fun subscribe(
        child: CoroutineScope,
        method: String,
        input: JsonObject = JsonObject(emptyMap()),
        receive: (JsonObject) -> Unit,
    ) {
        child.launch {
            var retryDelay = INITIAL_RETRY_DELAY
            while (isActive) {
                try {
                    client.stream(method, input).collect {
                        retryDelay = INITIAL_RETRY_DELAY
                        receive(it)
                    }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    ensureActive()
                    // Retry tools independently so older daemons remain usable.
                    error = "$method: ${failure.message}"
                }
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
            }
        }
    }

    fun call(
        method: String,
        input: JsonObject = JsonObject(emptyMap()),
        done: (JsonObject) -> Unit = {},
    ) {
        val current = generation
        scope.launch {
            try {
                val result = client.call(method, input)
                if (generation == current) done(result)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (generation == current) error = failure.message.orEmpty()
            }
        }
    }

    /**
     * Calls [method] and returns its failure message, or null on success. Auth dialogs show the
     * message inline, so it does not go to [error].
     */
    suspend fun perform(method: String, input: JsonObject): String? =
        try {
            client.call(method, input)
            null
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            failure.message ?: failure.toString()
        }
}
