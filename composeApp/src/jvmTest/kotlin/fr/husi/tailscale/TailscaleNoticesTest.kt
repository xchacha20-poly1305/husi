package fr.husi.tailscale

import kotlin.test.Test
import fr.husi.vpn.vpnAuthChallengeKey
import kotlin.test.assertEquals

class TailscaleNoticesTest {

    @Test
    fun `unread files in the first snapshot of an endpoint are a baseline`() {
        val current = listOf(endpoint(unread = 4))

        assertEquals(emptyList(), tailscaleNotices(emptyMap(), current))
    }

    @Test
    fun `a pending login in the first snapshot of an endpoint notifies`() {
        val current = listOf(
            endpoint(unread = 4, authUrl = "https://login.example/a", state = TAILSCALE_STATE_NEEDS_LOGIN),
        )

        assertEquals(listOf(TailscaleNotice.NeedsLogin("ts")), tailscaleNotices(emptyMap(), current))
    }

    @Test
    fun `an increase in unread files notifies the difference`() {
        val previous = listOf(endpoint(unread = 1)).toNoticeSnapshots()

        assertEquals(
            listOf(TailscaleNotice.FilesReceived("ts", 3)),
            tailscaleNotices(previous, listOf(endpoint(unread = 4))),
        )
    }

    @Test
    fun `an unchanged or smaller unread count does not notify`() {
        val same = listOf(endpoint(unread = 4)).toNoticeSnapshots()
        assertEquals(emptyList(), tailscaleNotices(same, listOf(endpoint(unread = 4))))

        val higher = listOf(endpoint(unread = 4)).toNoticeSnapshots()
        assertEquals(emptyList(), tailscaleNotices(higher, listOf(endpoint(unread = 2))))
    }

    @Test
    fun `a new auth url while login is required notifies`() {
        val previous = listOf(endpoint()).toNoticeSnapshots()

        assertEquals(
            listOf(TailscaleNotice.NeedsLogin("ts")),
            tailscaleNotices(
                previous,
                listOf(endpoint(authUrl = "https://login.example/a", state = TAILSCALE_STATE_NEEDS_LOGIN)),
            ),
        )
    }

    @Test
    fun `the same auth url does not notify again`() {
        val previous = listOf(
            endpoint(authUrl = "https://login.example/a", state = TAILSCALE_STATE_NEEDS_LOGIN),
        ).toNoticeSnapshots()

        assertEquals(
            emptyList(),
            tailscaleNotices(
                previous,
                listOf(endpoint(authUrl = "https://login.example/a", state = TAILSCALE_STATE_NEEDS_LOGIN)),
            ),
        )
    }

    @Test
    fun `an auth url without needs login does not notify`() {
        val previous = listOf(endpoint()).toNoticeSnapshots()

        assertEquals(
            emptyList(),
            tailscaleNotices(previous, listOf(endpoint(authUrl = "https://login.example/a"))),
        )
    }

    @Test
    fun `a changed auth url while login is required notifies`() {
        val previous = listOf(
            endpoint(authUrl = "https://login.example/a", state = TAILSCALE_STATE_NEEDS_LOGIN),
        ).toNoticeSnapshots()

        assertEquals(
            listOf(TailscaleNotice.NeedsLogin("ts")),
            tailscaleNotices(
                previous,
                listOf(endpoint(authUrl = "https://login.example/b", state = TAILSCALE_STATE_NEEDS_LOGIN)),
            ),
        )
    }

    @Test
    fun `needs login becoming true with an existing auth url notifies`() {
        val previous = listOf(endpoint(authUrl = "https://login.example/a")).toNoticeSnapshots()

        assertEquals(
            listOf(TailscaleNotice.NeedsLogin("ts")),
            tailscaleNotices(
                previous,
                listOf(endpoint(authUrl = "https://login.example/a", state = TAILSCALE_STATE_NEEDS_LOGIN)),
            ),
        )
    }
}

class TailscaleDashboardNoticeCountsTest {

    @Test
    fun `pending logins count once and unread files count each`() {
        val endpoints = listOf(
            endpoint(tag = "running", authUrl = "https://login.example/a"),
            endpoint(tag = "no-url", state = TAILSCALE_STATE_NEEDS_LOGIN),
            endpoint(tag = "login", authUrl = "https://login.example/b", state = TAILSCALE_STATE_NEEDS_LOGIN),
            endpoint(tag = "files", unread = 3),
        )

        val counts = endpoints.dashboardNoticeCounts()

        assertEquals(2, counts.size)
        assertEquals(1, counts[vpnAuthChallengeKey("login", "https://login.example/b")])
        assertEquals(3, counts.values.max())
    }
}

private fun endpoint(
    tag: String = "ts",
    unread: Int = 0,
    authUrl: String = "",
    state: String = TAILSCALE_STATE_RUNNING,
) = TailscaleEndpointState(
    endpointTag = tag,
    backendState = state,
    stateText = "",
    authUrl = authUrl,
    networkName = "",
    magicDnsSuffix = "",
    selfPeer = null,
    exitNode = null,
    userGroups = emptyList(),
    keyAuth = false,
    canShareFiles = false,
    waitingFileCount = 0,
    receivingFileCount = 0,
    unreadFileCount = unread,
)
