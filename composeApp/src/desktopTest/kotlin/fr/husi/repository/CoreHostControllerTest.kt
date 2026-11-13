package fr.husi.repository

import fr.husi.Key
import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.database.DataStore
import fr.husi.database.ProfileManager
import fr.husi.database.ProxyGroup
import fr.husi.database.SagerDatabase
import fr.husi.database.preference.reset
import fr.husi.fmt.LOCALHOST4
import fr.husi.fmt.socks.SOCKSBean
import fr.husi.ktx.applyDefaultValues
import fr.husi.platform.PlatformInfo
import fr.husi.proto.v1.Capabilities
import fr.husi.proto.v1.GetDaemonInfoResponse
import fr.husi.proto.v1.Hosting
import fr.husi.proto.v1.Ownership
import fr.husi.proto.v1.TunBackend
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
    fun `host discovery prefers an accessible daemon over spawning a session`() {
        tempDir.resolve("api.sock").createNewFile()
        val daemon = FakeCoreClient().also {
            it.nextDaemonInfo = GetDaemonInfoResponse(
                hosting = Hosting.HOSTING_DAEMON,
                api_version = CoreHostController.API_VERSION,
                ownership = Ownership(),
            )
        }
        val discovery = CoreHostController(
            repository,
            resolveCoreClient = { fakeClient },
            resolveCoreBinary = { error("Must use the available daemon") },
            dispatcher = dispatcher,
            daemonBasePath = tempDir.path,
            createDaemonClient = { daemon },
        )
        try {
            discovery.ensureHost()
            assertTrue(discovery.hostState.value.isDaemon)
            assertEquals(tempDir.path, repository.coreSocketBasePath)
            assertEquals(1, daemon.probeCalls)
        } finally {
            discovery.shutdownHost()
        }
    }

    @Test
    fun `missing inaccessible and broken daemon connections attempt the session core`() {
        for (failure in listOf(null, IOException("Permission denied"), IOException("Connection refused"))) {
            val socket = tempDir.resolve("api.sock")
            socket.delete()
            if (failure != null) socket.createNewFile()
            val daemon = FakeCoreClient().also { it.probeThrowable = failure }
            var sessionAttempted = false
            val discovery = CoreHostController(
                repository,
                resolveCoreClient = { fakeClient },
                resolveCoreBinary = {
                    sessionAttempted = true
                    null
                },
                dispatcher = dispatcher,
                daemonBasePath = tempDir.path,
                createDaemonClient = { daemon },
            )
            assertFailsWith<IOException> { discovery.ensureHost() }
            assertTrue(sessionAttempted)
            assertFalse(discovery.hostState.value.isDaemon)
            assertEquals(
                if (failure == null && !PlatformInfo.isWindows) {
                    0
                } else {
                    1
                },
                daemon.probeCalls,
            )
        }
    }

    @Test
    fun `foreign-owned daemon attaches read-only and does not take over`() = runTest {
        val info = GetDaemonInfoResponse(
            ownership = Ownership(
                claimed = true,
                owned_by_caller = false,
                owner_name = "alice",
                owner_id = "1001",
            ),
        )

        controller.tryClaimDaemon(fakeClient, info)

        assertEquals(0, fakeClient.takeOverServiceCalls)
        assertEquals(0, fakeClient.claimServiceCalls)
        assertEquals(
            DaemonOwner(name = "alice", id = "1001"), controller.hostState.value.foreignOwner,
        )
    }

    @Test
    fun `missing ownership is a response error and never claims the daemon`() = runTest {
        val error = assertFailsWith<IOException> {
            controller.tryClaimDaemon(fakeClient, GetDaemonInfoResponse())
        }

        assertEquals("Daemon response omitted ownership information", error.message)
        assertEquals(0, fakeClient.claimServiceCalls)
        assertEquals(0, fakeClient.takeOverServiceCalls)
    }

    @Test
    fun `missing ownership after takeover keeps the conflict and does not claim`() = runTest {
        val foreign = GetDaemonInfoResponse(
            ownership = Ownership(
                claimed = true,
                owned_by_caller = false,
                owner_name = "alice",
                owner_id = "1001",
            ),
        )
        controller.tryClaimDaemon(fakeClient, foreign)
        fakeClient.nextDaemonInfo = GetDaemonInfoResponse()

        assertFailsWith<IOException> { controller.takeOverDaemon() }

        assertEquals(1, fakeClient.takeOverServiceCalls)
        assertEquals(0, fakeClient.claimServiceCalls)
        assertEquals(DaemonOwner(name = "alice", id = "1001"), controller.hostState.value.foreignOwner)
    }

    @Test
    fun `unowned daemon claims without recording a conflict`() = runTest {
        val info = GetDaemonInfoResponse(ownership = Ownership(claimed = false))

        controller.tryClaimDaemon(fakeClient, info)

        assertEquals(0, fakeClient.takeOverServiceCalls)
        assertEquals(1, fakeClient.claimServiceCalls)
        assertNull(controller.hostState.value.foreignOwner)
    }

    @Test
    fun `own daemon does not claim or take over`() = runTest {
        val info = GetDaemonInfoResponse(
            ownership = Ownership(
                claimed = true,
                owned_by_caller = true,
                owner_name = "me",
                owner_id = "1000",
            ),
        )

        controller.tryClaimDaemon(fakeClient, info)

        assertEquals(0, fakeClient.takeOverServiceCalls)
        assertEquals(0, fakeClient.claimServiceCalls)
        assertNull(controller.hostState.value.foreignOwner)
    }

    @Test
    fun `takeOverDaemon calls takeOverService and clears the conflict`() = runTest {
        val foreign = GetDaemonInfoResponse(
            ownership = Ownership(
                claimed = true,
                owned_by_caller = false,
                owner_name = "bob",
                owner_id = "1002",
            ),
        )
        controller.tryClaimDaemon(fakeClient, foreign)
        fakeClient.nextDaemonInfo = GetDaemonInfoResponse(
            ownership = Ownership(
                claimed = true,
                owned_by_caller = true,
                owner_name = "me",
                owner_id = "1000",
            ),
        )

        controller.takeOverDaemon()

        assertEquals(1, fakeClient.takeOverServiceCalls)
        assertEquals(0, fakeClient.claimServiceCalls)
        assertNull(controller.hostState.value.foreignOwner)
    }

    @Test
    fun `takeOverDaemon claims when the daemon reports unclaimed after takeover`() = runTest {
        fakeClient.nextDaemonInfo = GetDaemonInfoResponse(ownership = Ownership(claimed = false))

        controller.takeOverDaemon()

        assertEquals(1, fakeClient.takeOverServiceCalls)
        assertEquals(1, fakeClient.claimServiceCalls)
        assertNull(controller.hostState.value.foreignOwner)
    }

    @Test
    fun `takeOverDaemon leaves the conflict in place when takeover fails`() = runTest {
        val foreign = GetDaemonInfoResponse(
            ownership = Ownership(
                claimed = true,
                owned_by_caller = false,
                owner_name = "carol",
                owner_id = "1003",
            ),
        )
        controller.tryClaimDaemon(fakeClient, foreign)
        fakeClient.takeOverServiceThrowable = IOException("denied")

        assertFailsWith<IOException> {
            controller.takeOverDaemon()
        }

        assertEquals(0, fakeClient.takeOverServiceCalls)
        assertEquals(
            DaemonOwner(name = "carol", id = "1003"), controller.hostState.value.foreignOwner,
        )
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

    @Test
    fun `NetworkManager session can start VPN without the system daemon`() = runTest(dispatcher.scheduler) {
        if (!PlatformInfo.isLinux) return@runTest
        val (sessionController, _) = newSystemProxyController()
        DataStore.serviceMode.set(Key.MODE_VPN)
        DataStore.networkManagerTun.set(true)
        fakeClient.nextDaemonInfo = GetDaemonInfoResponse(capabilities = Capabilities(network_manager_tun = true))
        DataStore.selectedProxy.set(createSocksProfile())
        sessionController.attachHostForTest(daemon = false)
        sessionController.attachSessionProcessForTest(ControllableProcess())

        sessionController.startLockedForTest()

        assertEquals(1, fakeClient.startServiceCalls)
        assertEquals(ServiceState.Connected, DataStore.serviceState)
        assertFalse(sessionController.hostState.value.isDaemon)
        assertEquals(TunBackend.TUN_BACKEND_NETWORK_MANAGER, fakeClient.lastStartServiceRequest?.options?.tun_backend)
        sessionController.stop()
        advanceUntilIdle()
    }

    @Test
    fun `NetworkManager is sent to the attached daemon without changing hosts`() = runTest(dispatcher.scheduler) {
        if (!PlatformInfo.isLinux) return@runTest
        val (daemonController, _) = newSystemProxyController()
        DataStore.serviceMode.set(Key.MODE_VPN)
        DataStore.networkManagerTun.set(true)
        DataStore.selectedProxy.set(createSocksProfile())
        fakeClient.nextDaemonInfo = GetDaemonInfoResponse(capabilities = Capabilities(network_manager_tun = true))
        daemonController.attachHostForTest(daemon = true)

        daemonController.startLockedForTest()

        assertTrue(daemonController.hostState.value.isDaemon)
        assertEquals(ServiceState.Connected, DataStore.serviceState)
        assertEquals(TunBackend.TUN_BACKEND_NETWORK_MANAGER, fakeClient.lastStartServiceRequest?.options?.tun_backend)

        daemonController.stop()
        advanceUntilIdle()
        DataStore.networkManagerTun.set(false)
        daemonController.startLockedForTest()

        assertTrue(daemonController.hostState.value.isDaemon)
        assertEquals(2, fakeClient.startServiceCalls)
        assertEquals(TunBackend.TUN_BACKEND_NATIVE, fakeClient.lastStartServiceRequest?.options?.tun_backend)
        daemonController.stop()
        advanceUntilIdle()
    }

    @Test
    fun `NetworkManager rejects a host that predates backend selection`() = runTest(dispatcher.scheduler) {
        if (!PlatformInfo.isLinux) return@runTest
        val (daemonController, _) = newSystemProxyController()
        DataStore.serviceMode.set(Key.MODE_VPN)
        DataStore.networkManagerTun.set(true)
        DataStore.selectedProxy.set(createSocksProfile())
        daemonController.attachHostForTest(daemon = true)

        daemonController.startLockedForTest()

        assertEquals(0, fakeClient.startServiceCalls)
        assertTrue(daemonController.hostState.value.isDaemon)
        daemonController.stop()
        advanceUntilIdle()
    }

    @Test
    fun `retries until the host serves requests`() = runTest {
        controller.attachSessionProcessForTest(ControllableProcess())
        fakeClient.probeAction = {
            if (fakeClient.probeCalls < 3) throw IOException("not listening yet")
        }

        controller.waitForHostReady()

        assertEquals(3, fakeClient.probeCalls)
    }

    @Test
    fun `timeout retains the connection failure`() = runTest {
        controller.attachSessionProcessForTest(ControllableProcess())
        val failure = IOException("transport 1 is not supported on this platform")
        fakeClient.probeThrowable = failure

        val error = assertFailsWith<IOException> {
            controller.waitForHostReady()
        }

        assertEquals(failure.message, generateSequence<Throwable>(error) { it.cause }.last().message)
        assertTrue(error.message.orEmpty().contains(failure.message!!))
    }

    @Test
    fun `timeout also bounds a stalled probe`() = runTest {
        controller.attachSessionProcessForTest(ControllableProcess())
        fakeClient.probeAction = { awaitCancellation() }

        val error = assertFailsWith<IOException> {
            controller.waitForHostReady()
        }

        assertTrue(error.message.orEmpty().contains("timed out"))
    }

    @Test
    fun `cancellation is propagated without retry`() = runTest {
        controller.attachSessionProcessForTest(ControllableProcess())
        val cancellation = CancellationException("cancelled")
        fakeClient.probeThrowable = cancellation

        assertEquals(
            cancellation.message,
            assertFailsWith<CancellationException> {
                controller.waitForHostReady()
            }.message,
        )
        assertEquals(1, fakeClient.probeCalls)
    }

    @Test
    fun `process exit retains the preceding connection failure`() = runTest {
        val process = ControllableProcess()
        controller.attachSessionProcessForTest(process)
        val failure = IOException("connection refused")
        fakeClient.probeAction = {
            process.alive = false
            throw failure
        }

        val error = assertFailsWith<IOException> {
            controller.waitForHostReady()
        }

        assertEquals(failure.message, generateSequence<Throwable>(error) { it.cause }.last().message)
        assertTrue(error.message.orEmpty().contains("exited"))
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

private class ControllableProcess : Process() {
    @Volatile
    var alive: Boolean = true

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
    override fun getInputStream(): InputStream = InputStream.nullInputStream()
    override fun getErrorStream(): InputStream = InputStream.nullInputStream()
    override fun waitFor(): Int = 0
    override fun exitValue(): Int = if (alive) throw IllegalThreadStateException() else 0
    override fun destroy() {
        alive = false
    }

    override fun isAlive(): Boolean = alive
}
