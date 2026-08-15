package fr.husi.core.remote

import fr.husi.core.CoreRpcException
import fr.husi.database.DataStore
import fr.husi.database.RemoteServer
import fr.husi.database.RemoteServerEntity
import fr.husi.proto.daemon.Version
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinMainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlManagerTest : HusiKoinMainDispatcherTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    private val localClient = FakeCoreClient()
    private val dao = FakeRemoteServerDao()

    private fun newManager(
        factory: RemoteClientFactory,
        probeInterval: Duration = 100.milliseconds,
        reconnectAfterFailures: Int = 3,
    ): RemoteControlManager {
        return RemoteControlManager(
            localClient = localClient,
            dao = dao,
            remoteClientFactory = factory,
            probeInterval = probeInterval,
            reconnectAfterFailures = reconnectAfterFailures,
            dispatcher = dispatcher,
        )
    }

    private fun server(id: Long = 1L): RemoteServer = RemoteServer(
        id = id,
        userOrder = 1L,
        name = "office",
        url = "http://127.0.0.1:9090",
        secret = "secret",
    )

    @Test
    fun `enterRemote probes then marks connected and switches client`() = runTest(dispatcher.scheduler) {
        val remote = FakeCoreClient().also { it.nextStartedAt = 1_700_000_000_000L }
        val created = mutableListOf<Pair<String, String>>()
        val manager = newManager(
            { url, secret ->
                created += url to secret
                remote
            },
        )
        try {
            runCurrent()
            manager.enterRemote(server())
            runCurrent()

            assertEquals(listOf("http://127.0.0.1:9090" to "secret"), created)
            assertEquals(remote, manager.activeClient.value)
            assertTrue(manager.isRemote)
            assertEquals(RemoteSessionState.CONNECTED, manager.session.value?.state)
            assertEquals(1_700_000_000_000L, manager.session.value?.startedAt)
            assertTrue(manager.targetConnected.value)
            assertEquals(1L, DataStore.activeRemoteServerId)
            assertTrue(remote.probeCalls > 0)
        } finally {
            manager.close()
        }
    }

    @Test
    fun `consecutive probe failures enter reconnecting then recover`() = runTest(dispatcher.scheduler) {
        val remote = FakeCoreClient()
        remote.probeThrowable = CoreRpcException("Unavailable", "down")
        val manager = newManager(
            factory = { _, _ -> remote },
            reconnectAfterFailures = 3,
        )
        try {
            runCurrent()
            manager.enterRemote(server())
            runCurrent()
            assertEquals(RemoteSessionState.CONNECTING, manager.session.value?.state)
            assertFalse(manager.targetConnected.value)

            advanceTimeBy(100.milliseconds)
            runCurrent()
            advanceTimeBy(100.milliseconds)
            runCurrent()
            assertEquals(RemoteSessionState.RECONNECTING, manager.session.value?.state)

            remote.probeThrowable = null
            advanceTimeBy(100.milliseconds)
            runCurrent()
            assertEquals(RemoteSessionState.CONNECTED, manager.session.value?.state)
            assertTrue(manager.targetConnected.value)
        } finally {
            manager.close()
        }
    }

    @Test
    fun `exitRemote closes remote client and restores local`() = runTest(dispatcher.scheduler) {
        val remote = FakeCoreClient()
        val manager = newManager({ _, _ -> remote })
        try {
            runCurrent()
            manager.enterRemote(server())
            runCurrent()
            manager.exitRemote()
            runCurrent()

            assertNull(manager.session.value)
            assertEquals(localClient, manager.activeClient.value)
            assertFalse(manager.isRemote)
            assertTrue(remote.closed)
            assertEquals(0L, DataStore.activeRemoteServerId)
        } finally {
            manager.close()
        }
    }

    @Test
    fun `restore reopens last remote session`() = runTest(dispatcher.scheduler) {
        val entity = RemoteServerEntity(
            id = 4L,
            userOrder = 1L,
            name = "lab",
            url = "http://10.0.0.2:9090",
            secret = "token",
        )
        dao.insert(entity)
        DataStore.activeRemoteServerId = 4L
        val remote = FakeCoreClient()
        val manager = newManager({ _, _ -> remote })
        try {
            runCurrent()

            assertEquals(4L, manager.session.value?.server?.id)
            assertEquals(remote, manager.activeClient.value)
            assertEquals(RemoteSessionState.CONNECTED, manager.session.value?.state)
        } finally {
            manager.close()
        }
    }

    @Test
    fun `delete of active server exits remote`() = runTest(dispatcher.scheduler) {
        val remote = FakeCoreClient()
        val entity = server().toEntity()
        dao.insert(entity)
        val manager = newManager({ _, _ -> remote })
        try {
            runCurrent()
            manager.enterRemote(entity.toModel())
            runCurrent()
            manager.deleteServer(entity.id)
            runCurrent()

            assertNull(manager.session.value)
            assertEquals(localClient, manager.activeClient.value)
            assertNull(dao.getById(entity.id))
        } finally {
            manager.close()
        }
    }

    @Test
    fun `testConnection returns daemon version`() = runTest(dispatcher.scheduler) {
        val probeClient = FakeCoreClient().also {
            it.nextDaemonVersion = Version.newBuilder().setVersion("1.14.0").build()
        }
        val manager = newManager({ _, _ -> probeClient })
        try {
            runCurrent()
            val result = manager.testConnection("127.0.0.1:9090", "secret")
            runCurrent()

            assertEquals("1.14.0", result.getOrThrow())
            assertTrue(probeClient.closed)
            assertTrue(probeClient.probeCalls > 0)
        } finally {
            manager.close()
        }
    }

    @Test
    fun `testConnection surfaces probe failure`() = runTest(dispatcher.scheduler) {
        val probeClient = FakeCoreClient().also {
            it.probeThrowable = CoreRpcException("Unauthenticated", "invalid authorization")
        }
        val manager = newManager({ _, _ -> probeClient })
        try {
            runCurrent()
            val result = manager.testConnection("127.0.0.1:9090", "wrong")
            runCurrent()

            assertTrue(result.isFailure)
            assertIs<CoreRpcException>(result.exceptionOrNull())
            assertTrue(probeClient.closed)
        } finally {
            manager.close()
        }
    }
}

private class FakeRemoteServerDao : RemoteServerEntity.Dao {
    private val items = MutableStateFlow<List<RemoteServerEntity>>(emptyList())
    private var nextId = 1L

    override fun list(): Flow<List<RemoteServerEntity>> = items

    override suspend fun getById(id: Long): RemoteServerEntity? =
        items.value.firstOrNull { it.id == id }

    override suspend fun nextOrder(): Long =
        (items.value.maxOfOrNull { it.userOrder } ?: 0L) + 1L

    override suspend fun insert(server: RemoteServerEntity): Long {
        if (server.id == 0L) {
            server.id = nextId++
        } else {
            nextId = maxOf(nextId, server.id + 1L)
        }
        items.value = items.value.filter { it.id != server.id } + server
        return server.id
    }

    override suspend fun update(server: RemoteServerEntity) {
        items.value = items.value.map { current ->
            if (current.id == server.id) server else current
        }
    }

    override suspend fun delete(id: Long) {
        items.value = items.value.filter { it.id != id }
    }
}
