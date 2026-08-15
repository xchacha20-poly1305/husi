package fr.husi.core.remote

import fr.husi.bg.BackendState
import fr.husi.core.CoreClient
import fr.husi.database.DataStore
import fr.husi.database.RemoteServer
import fr.husi.database.RemoteServerEntity
import fr.husi.ktx.Logs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class RemoteSessionState {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}

data class RemoteSession(
    val server: RemoteServer,
    val client: CoreClient,
    val state: RemoteSessionState,
    val startedAt: Long? = null,
    val lastError: String? = null,
)

fun interface RemoteClientFactory {
    fun create(serverURL: String, secret: String): CoreClient
}

class RemoteControlManager(
    private val localClient: CoreClient,
    private val dao: RemoteServerEntity.Dao,
    private val remoteClientFactory: RemoteClientFactory,
    private val probeInterval: Duration = DEFAULT_PROBE_INTERVAL,
    private val reconnectAfterFailures: Int = DEFAULT_RECONNECT_AFTER_FAILURES,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val access = Mutex()
    private var monitorJob: Job? = null

    val servers: Flow<List<RemoteServer>> = dao.list().map { entities ->
        entities.map { it.toModel() }
    }

    val session: StateFlow<RemoteSession?>
        field = MutableStateFlow(null)

    val activeClient: StateFlow<CoreClient>
        field = MutableStateFlow(localClient)

    val targetConnected: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val isRemote: Boolean
        get() = session.value != null

    val targetKey: Long
        get() = session.value?.server?.id ?: LOCAL_TARGET_ID

    init {
        scope.launch {
            combine(session, BackendState.status) { remoteSession, status ->
                if (remoteSession == null) {
                    status.state.connected
                } else {
                    remoteSession.state == RemoteSessionState.CONNECTED
                }
            }.collect { connected ->
                targetConnected.value = connected
            }
        }
        scope.launch {
            restore()
        }
    }

    private suspend fun restore() {
        val activeId = DataStore.activeRemoteServerId
        if (activeId <= LOCAL_TARGET_ID) return
        val entity = dao.getById(activeId) ?: return
        enterRemote(entity.toModel())
    }

    suspend fun enterRemote(server: RemoteServer) {
        access.withLock {
            closeSessionLocked(keepActiveId = true)
            DataStore.activeRemoteServerId = server.id
            val client = remoteClientFactory.create(server.url, server.secret)
            val next = RemoteSession(
                server = server,
                client = client,
                state = RemoteSessionState.CONNECTING,
            )
            session.value = next
            activeClient.value = client
            startMonitorLocked(client, server.id)
        }
    }

    suspend fun exitRemote() {
        access.withLock {
            closeSessionLocked(keepActiveId = false)
        }
    }

    suspend fun upsertServer(server: RemoteServer): RemoteServer {
        val entity = server.toEntity()
        val id = dao.upsert(entity)
        val saved = entity.toModel().copy(id = id)
        val current = session.value
        if (current?.server?.id == id) {
            if (current.server.url != saved.url || current.server.secret != saved.secret) {
                enterRemote(saved)
            } else {
                session.value = current.copy(server = saved)
            }
        }
        return saved
    }

    suspend fun deleteServer(id: Long) {
        if (session.value?.server?.id == id) {
            exitRemote()
        }
        dao.delete(id)
    }

    fun close() {
        scope.cancel()
    }

    /** [url] has to be normalized by [fr.husi.database.normalizeRemoteServerURL] first. */
    suspend fun testConnection(url: String, secret: String): Result<String> {
        val client = remoteClientFactory.create(url, secret)
        return try {
            client.probe()
            val version = client.getDaemonVersion().version
            Result.success(version)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { client.close() }
        }
    }

    private fun startMonitorLocked(client: CoreClient, serverId: Long) {
        monitorJob = scope.launch {
            var failures = 0
            while (isActive) {
                try {
                    client.probe()
                    failures = 0
                    val current = session.value
                    if (current == null || current.server.id != serverId) return@launch
                    val startedAt = current.startedAt
                        ?: runCatching { client.getStartedAt() }.getOrNull()?.takeIf { it > 0L }
                    if (current.state != RemoteSessionState.CONNECTED || current.startedAt != startedAt) {
                        session.value = current.copy(
                            state = RemoteSessionState.CONNECTED,
                            startedAt = startedAt,
                            lastError = null,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += 1
                    Logs.w("remote probe failed", e)
                    val current = session.value
                    if (current == null || current.server.id != serverId) return@launch
                    val nextState = if (failures >= reconnectAfterFailures) {
                        RemoteSessionState.RECONNECTING
                    } else {
                        current.state
                    }
                    session.value = current.copy(
                        state = nextState,
                        lastError = e.message,
                    )
                }
                delay(probeInterval)
            }
        }
    }

    private suspend fun closeSessionLocked(keepActiveId: Boolean) {
        monitorJob?.cancel()
        monitorJob = null
        val previous = session.value
        session.value = null
        activeClient.value = localClient
        if (!keepActiveId) {
            DataStore.activeRemoteServerId = LOCAL_TARGET_ID
        }
        if (previous != null) {
            runCatching { previous.client.close() }
        }
    }

    companion object {
        const val LOCAL_TARGET_ID = 0L
        val DEFAULT_PROBE_INTERVAL = 2.seconds
        const val DEFAULT_RECONNECT_AFTER_FAILURES = 3
    }
}
