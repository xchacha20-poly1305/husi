package fr.husi.repository

import fr.husi.Key
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.fmt.LOCALHOST4
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.ktx.applyDefaultValues
import fr.husi.proto.v1.getDaemonInfoResponse
import fr.husi.proto.v1.ownership
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CoreHostControllerTest : HusiKoinTest() {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var tempDir: File
    private lateinit var repository: DesktopRepository
    private lateinit var fakeClient: FakeCoreClient
    private lateinit var controller: CoreHostController

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
        SagerDatabase.proxyDao.reset()
        SagerDatabase.groupDao.reset()
        DataStore.inboundUsername.set("")
        DataStore.inboundPassword.set("")
        DataStore.mixedPort.set(2080)
        DataStore.serviceMode.set(Key.MODE_PROXY)
        DataStore.systemProxy.set(false)
    }

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("husi-core-host").toFile()
        repository = FakeDesktopRepository(tempDir)
        fakeClient = FakeCoreClient()
        controller = CoreHostController(
            repository,
            resolveCoreClient = { fakeClient },
            systemProxyBackend = RecordingSystemProxyBackend(),
        )
    }

    @AfterTest
    fun tearDown() {
        DataStore.serviceState = ServiceState.Idle
        BackendState.reset()
        tempDir.deleteRecursively()
    }

    private fun newSystemProxyController(
        backend: RecordingSystemProxyBackend = RecordingSystemProxyBackend(),
    ): Pair<CoreHostController, RecordingSystemProxyBackend> {
        val systemProxyController = CoreHostController(
            repository = repository,
            resolveCoreClient = { fakeClient },
            resolveCoreBinary = { null },
            dispatcher = dispatcher,
            systemProxyBackend = backend,
        )
        return systemProxyController to backend
    }

    @Test
    fun `foreign-owned daemon attaches read-only and does not take over`() = runTest {
        val info = getDaemonInfoResponse {
            ownership = ownership {
                claimed = true
                ownedByCaller = false
                ownerName = "alice"
                ownerId = "1001"
            }
        }

        controller.tryClaimDaemon(fakeClient, info)

        assertEquals(0, fakeClient.takeOverServiceCalls)
        assertEquals(0, fakeClient.claimServiceCalls)
        assertEquals(DaemonOwner(name = "alice", id = "1001"), controller.hostState.value.foreignOwner)
    }

    @Test
    fun `unowned daemon claims without recording a conflict`() = runTest {
        val info = getDaemonInfoResponse {
            ownership = ownership {
                claimed = false
            }
        }

        controller.tryClaimDaemon(fakeClient, info)

        assertEquals(0, fakeClient.takeOverServiceCalls)
        assertEquals(1, fakeClient.claimServiceCalls)
        assertNull(controller.hostState.value.foreignOwner)
    }

    @Test
    fun `own daemon does not claim or take over`() = runTest {
        val info = getDaemonInfoResponse {
            ownership = ownership {
                claimed = true
                ownedByCaller = true
                ownerName = "me"
                ownerId = "1000"
            }
        }

        controller.tryClaimDaemon(fakeClient, info)

        assertEquals(0, fakeClient.takeOverServiceCalls)
        assertEquals(0, fakeClient.claimServiceCalls)
        assertNull(controller.hostState.value.foreignOwner)
    }

    @Test
    fun `takeOverDaemon calls takeOverService and clears the conflict`() = runTest {
        val foreign = getDaemonInfoResponse {
            ownership = ownership {
                claimed = true
                ownedByCaller = false
                ownerName = "bob"
                ownerId = "1002"
            }
        }
        controller.tryClaimDaemon(fakeClient, foreign)
        fakeClient.nextDaemonInfo = getDaemonInfoResponse {
            ownership = ownership {
                claimed = true
                ownedByCaller = true
                ownerName = "me"
                ownerId = "1000"
            }
        }

        controller.takeOverDaemon()

        assertEquals(1, fakeClient.takeOverServiceCalls)
        assertEquals(0, fakeClient.claimServiceCalls)
        assertNull(controller.hostState.value.foreignOwner)
    }

    @Test
    fun `takeOverDaemon claims when the daemon reports unclaimed after takeover`() = runTest {
        fakeClient.nextDaemonInfo = getDaemonInfoResponse {
            ownership = ownership {
                claimed = false
            }
        }

        controller.takeOverDaemon()

        assertEquals(1, fakeClient.takeOverServiceCalls)
        assertEquals(1, fakeClient.claimServiceCalls)
        assertNull(controller.hostState.value.foreignOwner)
    }

    @Test
    fun `takeOverDaemon leaves the conflict in place when takeover fails`() = runTest {
        val foreign = getDaemonInfoResponse {
            ownership = ownership {
                claimed = true
                ownedByCaller = false
                ownerName = "carol"
                ownerId = "1003"
            }
        }
        controller.tryClaimDaemon(fakeClient, foreign)
        fakeClient.takeOverServiceThrowable = IOException("denied")

        assertFailsWith<IOException> {
            controller.takeOverDaemon()
        }

        assertEquals(0, fakeClient.takeOverServiceCalls)
        assertEquals(DaemonOwner(name = "carol", id = "1003"), controller.hostState.value.foreignOwner)
    }

    private suspend fun createSocksProfile(): Long {
        val group = ProxyGroup(name = "group").applyDefaultValues()
        group.id = SagerDatabase.groupDao.createGroup(group)
        val profile = ProfileManager.createProfile(
            group.id,
            SOCKSBean().apply {
                name = "socks"
                serverAddress = "127.0.0.1"
                serverPort = 1080
            },
        )
        return profile.id
    }

    @Test
    fun `turning the preference on while stopped does not call the backend and applies on start`() =
        runTest(dispatcher.scheduler) {
            val (systemProxyController, backend) = newSystemProxyController()
            DataStore.selectedProxy.set(createSocksProfile())
            DataStore.mixedPort.set(2080)
            DataStore.serviceState = ServiceState.Stopped
            systemProxyController.attachHostForTest(daemon = true)

            DataStore.systemProxy.set(true)
            advanceUntilIdle()

            assertTrue(backend.enableCalls.isEmpty())
            assertEquals(0, backend.disableCalls)
            assertTrue(DataStore.systemProxy.get())

            systemProxyController.startLockedForTest()

            assertEquals(listOf(LOCALHOST4 to 2080), backend.enableCalls)
            assertTrue(DataStore.systemProxy.get())
            assertEquals(ServiceState.Connected, DataStore.serviceState)
        }

    @Test
    fun `turning the preference on while connected calls enable and off calls disable`() =
        runTest(dispatcher.scheduler) {
            val (systemProxyController, backend) = newSystemProxyController()
            DataStore.mixedPort.set(3456)
            DataStore.serviceState = ServiceState.Connected

            DataStore.systemProxy.set(true)
            advanceUntilIdle()

            assertEquals(listOf(LOCALHOST4 to 3456), backend.enableCalls)

            DataStore.systemProxy.set(false)
            advanceUntilIdle()

            assertEquals(1, backend.disableCalls)
            assertFalse(DataStore.systemProxy.get())
        }

    @Test
    fun `inbound credentials keep the system proxy unset and clear an applied one`() =
        runTest(dispatcher.scheduler) {
            val (_, backend) = newSystemProxyController()
            DataStore.serviceState = ServiceState.Connected
            DataStore.inboundUsername.set("user")

            DataStore.systemProxy.set(true)
            advanceUntilIdle()

            assertTrue(backend.enableCalls.isEmpty())

            DataStore.inboundUsername.set("")
            advanceUntilIdle()

            assertEquals(listOf(LOCALHOST4 to 2080), backend.enableCalls)

            DataStore.inboundPassword.set("secret")
            advanceUntilIdle()

            assertEquals(1, backend.disableCalls)
            assertTrue(DataStore.systemProxy.get())
        }

    @Test
    fun `stop calls disable and the preference stays true`() = runTest(dispatcher.scheduler) {
        val (systemProxyController, backend) = newSystemProxyController()
        DataStore.serviceState = ServiceState.Connected
        systemProxyController.attachHostForTest(daemon = true)

        DataStore.systemProxy.set(true)
        advanceUntilIdle()

        systemProxyController.stop()
        advanceUntilIdle()

        assertEquals(1, backend.disableCalls)
        assertTrue(DataStore.systemProxy.get())
        assertEquals(ServiceState.Stopped, DataStore.serviceState)
    }

    @Test
    fun `reload clears and re-applies with the new port`() = runTest(dispatcher.scheduler) {
        val (systemProxyController, backend) = newSystemProxyController()
        DataStore.selectedProxy.set(createSocksProfile())
        DataStore.mixedPort.set(2080)
        DataStore.systemProxy.set(true)
        systemProxyController.attachHostForTest(daemon = true)

        systemProxyController.startLockedForTest()
        assertEquals(ServiceState.Connected, DataStore.serviceState)
        assertEquals(listOf(LOCALHOST4 to 2080), backend.enableCalls)

        DataStore.mixedPort.set(3456)
        systemProxyController.reloadLockedForTest()

        assertTrue(DataStore.systemProxy.get())
        assertEquals(ServiceState.Connected, DataStore.serviceState)
        assertEquals(1, backend.disableCalls)
        assertEquals(
            listOf(LOCALHOST4 to 2080, LOCALHOST4 to 3456),
            backend.enableCalls,
        )
    }

    @Test
    fun `an enable failure keeps the preference true and a following toggle off does not disable`() =
        runTest(dispatcher.scheduler) {
            val backend = RecordingSystemProxyBackend().apply {
                enableThrowable = IOException("denied")
            }
            val (systemProxyController, _) = newSystemProxyController(backend)
            DataStore.serviceState = ServiceState.Connected

            DataStore.systemProxy.set(true)
            advanceUntilIdle()

            assertTrue(DataStore.systemProxy.get())
            assertEquals(listOf(LOCALHOST4 to 2080), backend.enableCalls)

            DataStore.systemProxy.set(false)
            advanceUntilIdle()

            assertEquals(0, backend.disableCalls)
            assertFalse(DataStore.systemProxy.get())
        }
}

private class RecordingSystemProxyBackend : SystemProxyBackend {
    val enableCalls = mutableListOf<Pair<String, Int>>()
    var disableCalls = 0
    var enableThrowable: Throwable? = null

    override fun enable(host: String, port: Int) {
        enableCalls += host to port
        enableThrowable?.let { throw it }
    }

    override fun disable() {
        disableCalls += 1
    }
}
