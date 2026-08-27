package fr.husi.repository

import fr.husi.ktx.deleteRecursively
import fr.husi.proto.v1.getDaemonInfoResponse
import fr.husi.proto.v1.ownership
import fr.husi.test.FakeCoreClient
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CoreHostControllerTest {

    private lateinit var tempDir: PlatformFile
    private lateinit var repository: DesktopRepository
    private lateinit var fakeClient: FakeCoreClient
    private lateinit var controller: CoreHostController

    @BeforeTest
    fun setUp() {
        tempDir = PlatformFile(createTempDirectory("husi-core-host").toString())
        repository = DesktopRepository(tempDir)
        fakeClient = FakeCoreClient()
        controller = CoreHostController(repository, resolveCoreClient = { fakeClient })
    }

    @AfterTest
    fun tearDown() {
        runBlocking { tempDir.deleteRecursively() }
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
}
