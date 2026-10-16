package fr.husi.tailscale

import fr.husi.core.remote.CoreTarget
import fr.husi.test.FakeCoreClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TailscalePresenceTest {

    @Test
    fun `an initial empty endpoint list does not close the screen`() {
        assertFalse(TailscalePresence.endpoint("ts").shouldClose(snapshot(emptyList())))
    }

    @Test
    fun `an initial list that contains the endpoint does not close the screen`() {
        assertFalse(TailscalePresence.endpoint("ts").shouldClose(snapshot(listOf(endpoint("ts")))))
    }

    @Test
    fun `an initial list of other endpoints closes the screen`() {
        assertTrue(TailscalePresence.endpoint("ts").shouldClose(snapshot(listOf(endpoint("other")))))
    }

    @Test
    fun `a later empty list closes the screen after the endpoint was seen`() {
        val presence = TailscalePresence.endpoint("ts")
        assertFalse(presence.shouldClose(snapshot(listOf(endpoint("ts")))))

        assertTrue(presence.shouldClose(snapshot(emptyList())))
    }

    @Test
    fun `a later list without the endpoint closes the screen`() {
        val presence = TailscalePresence.endpoint("ts")
        assertFalse(presence.shouldClose(snapshot(listOf(endpoint("ts")))))

        assertTrue(presence.shouldClose(snapshot(listOf(endpoint("other")))))
    }

    @Test
    fun `an endpoint that arrives after the initial empty list does not close the screen`() {
        val presence = TailscalePresence.endpoint("ts")
        assertFalse(presence.shouldClose(snapshot(emptyList())))

        assertFalse(presence.shouldClose(snapshot(listOf(endpoint("ts")))))
    }

    @Test
    fun `an initial empty peer list does not close the screen`() {
        assertFalse(TailscalePresence.peer("ts", "laptop").shouldClose(snapshot(emptyList())))
    }

    @Test
    fun `an initial endpoint without the peer closes the screen`() {
        assertTrue(TailscalePresence.peer("ts", "laptop").shouldClose(snapshot(listOf(endpoint("ts")))))
    }

    @Test
    fun `a peer that disappears later closes the screen`() {
        val presence = TailscalePresence.peer("ts", "laptop")
        assertFalse(presence.shouldClose(snapshot(listOf(endpoint("ts", peerId = "laptop")))))

        assertTrue(presence.shouldClose(snapshot(listOf(endpoint("ts")))))
    }

    @Test
    fun `the screen binds to the first core that reports`() {
        val presence = TailscalePresence.endpoint("ts")
        assertFalse(presence.shouldClose(TailscaleSnapshot()))
        assertFalse(presence.shouldClose(snapshot(listOf(endpoint("ts")))))

        assertEquals(localTarget, presence.requireTarget())
    }

    @Test
    fun `another core closes the screen even with the same endpoint tag`() {
        val presence = TailscalePresence.endpoint("ts")
        assertFalse(presence.shouldClose(snapshot(listOf(endpoint("ts")))))

        val remote = CoreTarget(FakeCoreClient(), remoteServerId = 1L)
        assertTrue(presence.shouldClose(snapshot(listOf(endpoint("ts")), target = remote)))
        assertEquals(localTarget, presence.requireTarget())
    }
}

private val localTarget = CoreTarget(FakeCoreClient())

private fun snapshot(
    endpoints: List<TailscaleEndpointState>,
    target: CoreTarget? = localTarget,
) = TailscaleSnapshot(target, endpoints)

private fun endpoint(tag: String, peerId: String? = null) = TailscaleEndpointState(
    endpointTag = tag,
    backendState = TAILSCALE_STATE_RUNNING,
    stateText = "",
    authUrl = "",
    networkName = "",
    magicDnsSuffix = "",
    selfPeer = peerId?.let { peer(it) },
    exitNode = null,
    userGroups = emptyList(),
    keyAuth = false,
    canShareFiles = false,
    waitingFileCount = 0,
    receivingFileCount = 0,
    unreadFileCount = 0,
)

private fun peer(id: String) = TailscalePeerState(
    id = id,
    stableId = id,
    hostName = id,
    dnsName = "",
    operatingSystem = "",
    tailscaleIps = emptyList(),
    sshHostKeys = emptyList(),
    online = true,
    exitNode = false,
    exitNodeOption = false,
    shareeNode = false,
    expired = false,
    active = false,
    receivedBytes = 0L,
    transmittedBytes = 0L,
    keyExpiry = 0L,
    lastSeen = 0L,
    canReceiveFiles = false,
)
