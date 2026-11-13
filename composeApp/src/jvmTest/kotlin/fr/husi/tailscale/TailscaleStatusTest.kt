package fr.husi.tailscale

import fr.husi.core.remote.CoreTarget
import fr.husi.proto.daemon.TailscaleEndpointStatus
import fr.husi.proto.daemon.TailscalePeer
import fr.husi.proto.daemon.TailscaleStatusUpdate
import fr.husi.proto.daemon.TailscaleUserGroup
import fr.husi.test.FakeCoreClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TailscaleStatusTest {

    @Test
    fun `peer id prefers the dns name and the display name is its first label`() {
        val peer = TailscalePeer(hostName = "other", dnsName = "laptop.tailnet.ts.net").toState()

        assertEquals("laptop.tailnet.ts.net", peer.id)
        assertEquals("laptop", peer.displayName)
    }

    @Test
    fun `the dns name drops the trailing root dot`() {
        val peer = TailscalePeer(hostName = "other", dnsName = "laptop.tailnet.ts.net.").toState()

        assertEquals("laptop.tailnet.ts.net", peer.dnsName)
        assertEquals("laptop.tailnet.ts.net", peer.id)
    }

    @Test
    fun `peer id and display name fall back to the host name`() {
        val peer = TailscalePeer(hostName = "laptop").toState()

        assertEquals("laptop", peer.id)
        assertEquals("laptop", peer.displayName)
    }

    @Test
    fun `a dns name without a dot is the display name`() {
        val peer = TailscalePeer(hostName = "other", dnsName = "laptop").toState()

        assertEquals("laptop", peer.id)
        assertEquals("laptop", peer.displayName)
    }

    @Test
    fun `endpoint status maps self, the exit node, and grouped peers`() {
        val endpoint = TailscaleEndpointStatus(
            endpointTag = "ts",
            backendState = "Running",
            stateText = "Running",
            authURL = "https://login.tailscale.com/a/x",
            networkName = "tailnet",
            magicDNSSuffix = "tailnet.ts.net",
            self = TailscalePeer(
                hostName = "phone",
                dnsName = "phone.tailnet.ts.net",
                stableID = "self",
            ),
            exitNode = TailscalePeer(hostName = "exit-host", stableID = "exit"),
            userGroups = listOf(
                TailscaleUserGroup(
                    userID = 7L,
                    loginName = "ada@example.com",
                    displayName = "Ada",
                    profilePicURL = "https://example.com/ada.png",
                    peers = listOf(
                        TailscalePeer(
                            hostName = "laptop",
                            dnsName = "laptop.tailnet.ts.net",
                            os = "linux",
                            tailscaleIPs = listOf("100.64.0.2"),
                            online = true,
                            exitNodeOption = true,
                            active = true,
                            rxBytes = 1L,
                            txBytes = 2L,
                            keyExpiry = 3L,
                            stableID = "peer",
                            sshHostKeys = listOf("ssh-ed25519 AAAA"),
                            lastSeen = 4L,
                            canReceiveFiles = true,
                        ),
                    ),
                ),
            ),
            keyAuth = true,
            canShareFiles = true,
            waitingFileCount = 1,
            receivingFileCount = 2,
            unreadFileCount = 3,
        ).toState()

        assertEquals("ts", endpoint.endpointTag)
        assertEquals("Running", endpoint.backendState)
        assertEquals("https://login.tailscale.com/a/x", endpoint.authUrl)
        assertEquals("tailnet.ts.net", endpoint.magicDnsSuffix)
        assertEquals("phone.tailnet.ts.net", endpoint.selfPeer?.id)
        assertEquals("phone", endpoint.selfPeer?.displayName)
        assertEquals("exit-host", endpoint.exitNode?.id)
        assertEquals("exit-host", endpoint.exitNode?.displayName)
        val peer = endpoint.userGroups.single().peers.single()
        assertEquals("laptop.tailnet.ts.net", peer.id)
        assertEquals("laptop", peer.displayName)
        assertEquals("peer", peer.stableId)
        assertEquals("linux", peer.operatingSystem)
        assertEquals(listOf("100.64.0.2"), peer.tailscaleIps)
        assertEquals(listOf("ssh-ed25519 AAAA"), peer.sshHostKeys)
        assertEquals(1L, peer.receivedBytes)
        assertEquals(2L, peer.transmittedBytes)
        assertTrue(peer.online)
        assertTrue(peer.canReceiveFiles)
        assertEquals(7L, endpoint.userGroups.single().userId)
        assertEquals("https://example.com/ada.png", endpoint.userGroups.single().profilePictureUrl)
        assertEquals(3, endpoint.taildropFileCount)
        assertEquals(3, endpoint.unreadFileCount)
        assertTrue(endpoint.keyAuth)
        assertTrue(endpoint.canShareFiles)
        assertTrue(endpoint.hasExitNodeCandidates)
    }

    @Test
    fun `absent self and exit node stay null`() {
        val endpoint = TailscaleEndpointStatus(endpointTag = "ts").toState()

        assertNull(endpoint.selfPeer)
        assertNull(endpoint.exitNode)
        assertEquals(emptyList(), endpoint.userGroups)
        assertFalse(endpoint.hasExitNodeCandidates)
    }

    @Test
    fun `this device alone is not an exit node candidate`() {
        val endpoint = TailscaleEndpointStatus(
            endpointTag = "ts",
            self = TailscalePeer(hostName = "phone", stableID = "self", exitNodeOption = true),
            userGroups = listOf(
                TailscaleUserGroup(
                    peers = listOf(
                        TailscalePeer(
                            hostName = "phone",
                            stableID = "self",
                            exitNodeOption = true,
                        ),
                    ),
                ),
            ),
        ).toState()

        assertFalse(endpoint.hasExitNodeCandidates)
    }

    @Test
    fun `repository publishes mapped endpoints and an empty update clears them`() = runTest {
        val coreClient = FakeCoreClient()
        val target = CoreTarget(coreClient)
        val repository = TailscaleStatusRepository(MutableStateFlow(target), this)

        val collecting = launch { repository.snapshot.collect { } }
        advanceUntilIdle()
        coreClient.tailscaleStatusFlow.emit(
            TailscaleStatusUpdate(
                endpoints = listOf(
                    TailscaleEndpointStatus(
                        endpointTag = "ts",
                        self = TailscalePeer(hostName = "phone", dnsName = "phone.tailnet.ts.net"),
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(target, repository.snapshot.value.target)
        val peer = repository.snapshot.value.endpoints.single().selfPeer
        assertEquals("phone.tailnet.ts.net", peer?.id)
        assertEquals("phone", peer?.displayName)

        coreClient.tailscaleStatusFlow.emit(TailscaleStatusUpdate())
        advanceUntilIdle()
        assertEquals(emptyList(), repository.snapshot.value.endpoints)
        collecting.cancel()
        coroutineContext.cancelChildren()
    }

    @Test
    fun `repository clears endpoints when the target stops running`() = runTest {
        val coreClient = FakeCoreClient()
        val targets = MutableStateFlow<CoreTarget?>(CoreTarget(coreClient))
        val repository = TailscaleStatusRepository(targets, this)

        val collecting = launch { repository.snapshot.collect { } }
        advanceUntilIdle()
        coreClient.tailscaleStatusFlow.emit(
            TailscaleStatusUpdate(endpoints = listOf(TailscaleEndpointStatus(endpointTag = "ts"))),
        )
        advanceUntilIdle()
        assertEquals("ts", repository.snapshot.value.endpoints.single().endpointTag)

        targets.value = null
        advanceUntilIdle()
        assertEquals(TailscaleSnapshot(), repository.snapshot.value)
        collecting.cancel()
        coroutineContext.cancelChildren()
    }

    @Test
    fun `repository follows a switch to a remote core`() = runTest {
        val localClient = FakeCoreClient()
        val remoteClient = FakeCoreClient()
        val targets = MutableStateFlow<CoreTarget?>(CoreTarget(localClient))
        val repository = TailscaleStatusRepository(targets, this)

        val collecting = launch { repository.snapshot.collect { } }
        advanceUntilIdle()
        localClient.tailscaleStatusFlow.emit(
            TailscaleStatusUpdate(
                endpoints = listOf(TailscaleEndpointStatus(endpointTag = "local")),
            ),
        )
        remoteClient.tailscaleStatusFlow.emit(
            TailscaleStatusUpdate(
                endpoints = listOf(TailscaleEndpointStatus(endpointTag = "remote")),
            ),
        )
        advanceUntilIdle()
        assertEquals("local", repository.snapshot.value.endpoints.single().endpointTag)

        val remoteTarget = CoreTarget(remoteClient, remoteServerId = 1L)
        targets.value = remoteTarget
        advanceUntilIdle()
        assertEquals(remoteTarget, repository.snapshot.value.target)
        assertEquals("remote", repository.snapshot.value.endpoints.single().endpointTag)
        collecting.cancel()
        coroutineContext.cancelChildren()
    }
}
