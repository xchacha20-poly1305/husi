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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
}

data class RemoteSession(
    val server: RemoteServer,
    val client: CoreClient,
    val state: RemoteSessionState,
    val startedAt: Long? = null,
)

data class RemoteSessionFailure(
    val server: RemoteServer,
    val wasConnected: Boolean,
    val message: String,
)

fun interface RemoteClientFactory {
    fun create(serverURL: String, secret: String): CoreClient
}

class RemoteControlManager(
    private val localClient: CoreClient,
    private val dao: RemoteServerEntity.Dao,
    private val remoteClientFactory: RemoteClientFactory,
    private val probeInterval: Duration = DEFAULT_PROBE_INTERVAL,
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

    val failures: SharedFlow<RemoteSessionFailure>
        field = MutableSharedFlow(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

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
        val activeId = DataStore.activeRemoteServerId.get()
        if (activeId <= LOCAL_TARGET_ID) return
        val entity = dao.getById(activeId) ?: return
        enterRemote(entity.toModel())
    }

    suspend fun enterRemote(server: RemoteServer) {
        access.withLock {
            closeSessionLocked(keepActiveId = true)
            DataStore.activeRemoteServerId.set(server.id)
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

    /**
     * Probes [client] until the session ends. The first failed probe ends the
     * session and falls back to the local device: staying in remote mode would
     * only make every command fail at the point of use.
     */
    private fun startMonitorLocked(client: CoreClient, serverId: Long) {
        monitorJob = scope.launch {
            while (isActive) {
                try {
                    client.probe()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logs.w("remote probe failed", e)
                    failSession(serverId, e)
                    return@launch
                }
                val current = session.value
                if (current == null || current.server.id != serverId) return@launch
                val startedAt = current.startedAt
                    ?: runCatching { client.getStartedAt() }.getOrNull()?.takeIf { it > 0L }
                if (current.state != RemoteSessionState.CONNECTED || current.startedAt != startedAt) {
                    session.value = current.copy(
                        state = RemoteSessionState.CONNECTED,
                        startedAt = startedAt,
                    )
                }
                delay(probeInterval)
            }
        }
    }

    private suspend fun failSession(serverId: Long, error: Exception) {
        val failure = access.withLock {
            val current = session.value
            if (current == null || current.server.id != serverId) return
            // The caller is the monitor itself; it ends by returning,
            // so it must not be canceled halfway through closing the session.
            monitorJob = null
            closeSessionLocked(keepActiveId = false)
            RemoteSessionFailure(
                server = current.server,
                wasConnected = current.state == RemoteSessionState.CONNECTED,
                message = error.message ?: error.toString(),
            )
        }
        failures.emit(failure)
    }

    private suspend fun closeSessionLocked(keepActiveId: Boolean) {
        monitorJob?.cancel()
        monitorJob = null
        val previous = session.value
        session.value = null
        activeClient.value = localClient
        if (!keepActiveId) {
            DataStore.activeRemoteServerId.set(LOCAL_TARGET_ID)
        }
        if (previous != null) {
            runCatching { previous.client.close() }
        }
    }

    companion object {
        const val LOCAL_TARGET_ID = 0L
        val DEFAULT_PROBE_INTERVAL = 2.seconds
    }
}
