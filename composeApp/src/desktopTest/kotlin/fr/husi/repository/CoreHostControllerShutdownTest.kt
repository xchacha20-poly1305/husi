package fr.husi.repository

import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.database.DataStore
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinTest
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class CoreHostControllerShutdownTest : HusiKoinTest() {

    private lateinit var tempDir: File
    private lateinit var fakeClient: FakeCoreClient
    private lateinit var controller: CoreHostController

    @BeforeTest
    fun setUpController() {
        tempDir = createTempDirectory("husi-core-host-shutdown").toFile()
        fakeClient = FakeCoreClient()
        // No core binary: a session restart is attempted but never spawns.
        controller = CoreHostController(
            repository = FakeDesktopRepository(tempDir),
            resolveCoreClient = { fakeClient },
            resolveCoreBinary = { null },
        )
    }

    @AfterTest
    fun tearDownController() {
        DataStore.serviceState = ServiceState.Idle
        BackendState.reset()
        tempDir.deleteRecursively()
    }

    @Test
    fun `shutdownHost stops the box instance when attached to a daemon`() {
        DataStore.serviceState = ServiceState.Connected
        controller.attachHostForTest(daemon = true)

        controller.shutdownHost()

        assertEquals(1, fakeClient.stopServiceCalls)
        assertEquals(true, fakeClient.closed)
        assertFalse(controller.hostState.value.isDaemon)
        assertEquals(ServiceState.Stopped, DataStore.serviceState)
    }

    @Test
    fun `stop restarts a session host that refuses to stop`() {
        DataStore.serviceState = ServiceState.Connected
        controller.attachHostForTest(daemon = false)
        fakeClient.stopServiceThrowable = IOException("stuck")

        runBlocking { controller.stop().join() }

        assertEquals(1, fakeClient.stopServiceCalls)
        assertEquals(1, controller.sessionRestarts)
        assertEquals(ServiceState.Stopped, DataStore.serviceState)
    }

    @Test
    fun `shutdownHost does not detach a daemon host that refuses to stop`() {
        DataStore.serviceState = ServiceState.Connected
        controller.attachHostForTest(daemon = true)
        fakeClient.stopServiceThrowable = IOException("stuck")

        controller.shutdownHost()

        assertEquals(0, controller.daemonDetaches)
        assertEquals(ServiceState.Stopped, DataStore.serviceState)
    }

    @Test
    fun `shutdownHost does not restart a session host that refuses to stop`() {
        DataStore.serviceState = ServiceState.Connected
        controller.attachHostForTest(daemon = false)
        fakeClient.stopServiceThrowable = IOException("stuck")

        controller.shutdownHost()

        assertEquals(0, controller.sessionRestarts)
        assertEquals(ServiceState.Stopped, DataStore.serviceState)
    }

    @Test
    fun `stop failure against a daemon detaches instead of restarting a session`() {
        DataStore.serviceState = ServiceState.Connected
        controller.attachHostForTest(daemon = true)
        fakeClient.stopServiceThrowable = IOException("stuck")

        runBlocking { controller.stop().join() }

        assertEquals(0, controller.sessionRestarts)
        assertEquals(1, controller.daemonDetaches)
        // The daemon ends its own process; the next start attaches to the
        // replacement rather than reusing a client bound to the dead one.
        assertFalse(controller.hostState.value.isDaemon)
        assertEquals(ServiceState.Stopped, DataStore.serviceState)
    }

    @Test
    fun `shutdownHost gives up on a box instance that never answers`() {
        DataStore.serviceState = ServiceState.Connected
        controller.attachHostForTest(daemon = false)
        fakeClient.stopServiceDelay = 1.hours

        val elapsed = measureTime { controller.shutdownHost() }

        // Bounded by the shutdown budget, not by the far longer stop timeout.
        assertTrue(elapsed < 30.seconds, "shutdownHost blocked for $elapsed")
        assertEquals(0, controller.sessionRestarts)
    }

    @Test
    fun `shutdownHost stops the box instance in session mode`() {
        DataStore.serviceState = ServiceState.Connected
        controller.attachHostForTest(daemon = false)

        controller.shutdownHost()

        assertEquals(1, fakeClient.stopServiceCalls)
        assertEquals(ServiceState.Stopped, DataStore.serviceState)
    }
}
