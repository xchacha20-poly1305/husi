package fr.husi.repository

import fr.husi.bg.BackendState
import fr.husi.bg.ServiceState
import fr.husi.database.DataStore
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CoreHostControllerShutdownTest : HusiKoinTest() {

    private lateinit var tempDir: File
    private lateinit var fakeClient: FakeCoreClient
    private lateinit var controller: CoreHostController

    @BeforeTest
    fun setUpController() {
        tempDir = createTempDirectory("husi-core-host-shutdown").toFile()
        fakeClient = FakeCoreClient()
        controller = CoreHostController(DesktopRepository(tempDir)) { fakeClient }
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
    fun `shutdownHost stops the box instance in session mode`() {
        DataStore.serviceState = ServiceState.Connected
        controller.attachHostForTest(daemon = false)

        controller.shutdownHost()

        assertEquals(1, fakeClient.stopServiceCalls)
        assertEquals(ServiceState.Stopped, DataStore.serviceState)
    }
}
