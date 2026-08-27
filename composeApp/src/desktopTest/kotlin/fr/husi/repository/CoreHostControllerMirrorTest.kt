package fr.husi.repository

import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.database.DataStore
import fr.husi.ktx.deleteRecursively
import fr.husi.proto.daemon.ServiceStatus
import fr.husi.proto.daemon.serviceStatus
import fr.husi.proto.v1.clientMetadata
import fr.husi.proto.v1.getClientMetadataResponse
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinTest
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The core owns the service state; this controller only mirrors it. These
 * tests drive that mirror from the core side, i.e. changes nobody asked the UI
 * for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoreHostControllerMirrorTest : HusiKoinTest() {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var tempDir: PlatformFile
    private lateinit var fakeClient: FakeCoreClient
    private lateinit var controller: CoreHostController

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    @BeforeTest
    fun setUpController() {
        tempDir = PlatformFile(createTempDirectory("husi-core-host-mirror").toString())
        fakeClient = FakeCoreClient()
        controller = CoreHostController(
            repository = DesktopRepository(tempDir),
            resolveCoreClient = { fakeClient },
            // No core binary: a session restart is attempted but never spawns.
            resolveCoreBinary = { null },
            dispatcher = dispatcher,
        )
    }

    @AfterTest
    fun tearDownController() {
        DataStore.serviceState = ServiceState.Idle
        BackendState.reset()
        runBlocking { tempDir.deleteRecursively() }
    }

    /**
     * The preference store writes on a dispatcher of its own, out of reach of
     * the test scheduler, so this waits in real time rather than virtual.
     */
    private suspend fun awaitCurrentProfile(expected: Long) {
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                while (DataStore.currentProfile.get() != expected) {
                    delay(10.milliseconds)
                }
            }
        }
    }

    private suspend fun report(type: ServiceStatus.Type, errorMessage: String = "") {
        fakeClient.serviceStatusFlow.emit(
            serviceStatus {
                status = type
                this.errorMessage = errorMessage
            },
        )
    }

    @Test
    fun `attaching to a host that is already running adopts its service`() =
        runTest(dispatcher.scheduler) {
            fakeClient.nextClientMetadata = getClientMetadataResponse {
                clientMetadata = clientMetadata {
                    profileId = 7L
                    profileName = "office"
                }
            }
            controller.attachHostForTest(daemon = true)

            report(ServiceStatus.Type.STARTED)
            advanceUntilIdle()

            assertEquals(ServiceState.Connected, DataStore.serviceState)
            assertEquals(ServiceState.Connected, BackendState.status.value.state)
            assertEquals("office", BackendState.status.value.profileName)
            assertTrue(BackendState.connected.value)
            awaitCurrentProfile(7L)
        }

    @Test
    fun `a service that goes away behind the UI stops the local side`() =
        runTest(dispatcher.scheduler) {
            DataStore.serviceState = ServiceState.Connected
            controller.attachHostForTest(daemon = true)

            report(ServiceStatus.Type.IDLE)
            advanceUntilIdle()

            assertEquals(ServiceState.Stopped, DataStore.serviceState)
            assertEquals(ServiceState.Stopped, BackendState.status.value.state)
            assertFalse(BackendState.connected.value)
            assertEquals(1, fakeClient.stopServiceCalls)
        }

    @Test
    fun `a core that reports a fatal error stops the local side`() =
        runTest(dispatcher.scheduler) {
            DataStore.serviceState = ServiceState.Connected
            controller.attachHostForTest(daemon = true)

            report(ServiceStatus.Type.FATAL, errorMessage = "listen tcp :2080: address in use")
            advanceUntilIdle()

            assertEquals(ServiceState.Stopped, DataStore.serviceState)
            assertFalse(BackendState.connected.value)
        }

    @Test
    fun `a core starting a service the UI did not start shows as connecting`() =
        runTest(dispatcher.scheduler) {
            controller.attachHostForTest(daemon = true)

            report(ServiceStatus.Type.STARTING)
            advanceUntilIdle()

            assertEquals(ServiceState.Connecting, DataStore.serviceState)
            assertEquals(ServiceState.Connecting, BackendState.status.value.state)
        }

    @Test
    fun `an idle core leaves a UI that shows nothing running alone`() =
        runTest(dispatcher.scheduler) {
            controller.attachHostForTest(daemon = true)

            report(ServiceStatus.Type.IDLE)
            advanceUntilIdle()

            assertEquals(ServiceState.Idle, DataStore.serviceState)
            assertEquals(0, fakeClient.stopServiceCalls)
        }
}
