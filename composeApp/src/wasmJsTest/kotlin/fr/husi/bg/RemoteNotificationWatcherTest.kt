@file:OptIn(ExperimentalCoroutinesApi::class)

package fr.husi.bg

import fr.husi.Key
import fr.husi.database.RemoteServer
import fr.husi.database.preference.long
import fr.husi.proto.daemon.OpenConnectAuthChallenge
import fr.husi.proto.daemon.OpenConnectEndpointStatus
import fr.husi.proto.daemon.OpenConnectStatusUpdate
import fr.husi.proto.daemon.OpenVPNChallenge
import fr.husi.proto.daemon.OpenVPNEndpointStatus
import fr.husi.proto.daemon.OpenVPNStatusUpdate
import fr.husi.proto.daemon.TailscaleEndpointStatus
import fr.husi.proto.daemon.TailscaleStatusUpdate
import fr.husi.proto.daemon.Version
import fr.husi.remote.RemoteState
import fr.husi.repository.Repository
import fr.husi.resources.Res
import fr.husi.resources.auth_required
import fr.husi.resources.openconnect_authentication
import fr.husi.resources.openvpn_authentication
import fr.husi.resources.taildrop_received_notification
import fr.husi.resources.tailscale_state_needs_login
import fr.husi.resources.tailscale_with_tag
import fr.husi.tailscale.TAILSCALE_STATE_NEEDS_LOGIN
import fr.husi.tailscale.TAILSCALE_STATE_RUNNING
import fr.husi.test.FakeBrowserNotificationClient
import fr.husi.test.FakePreferencesDataStore
import fr.husi.test.FakeRemoteClient
import fr.husi.test.FakeRemoteServerStore
import fr.husi.ui.RemoteRoute
import fr.husi.vpn.OPENCONNECT_STATE_AUTH_PENDING
import fr.husi.vpn.OPENCONNECT_STATE_CONNECTED
import fr.husi.vpn.OPENVPN_CHALLENGE_CREDENTIALS
import fr.husi.vpn.OPENVPN_STATE_AUTH_PENDING
import fr.husi.vpn.OPENVPN_STATE_CONNECTED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class RemoteNotificationWatcherTest {

    private val serverA = RemoteServer(id = 1L, name = "Home", url = "https://home.example", secret = "s1")
    private val serverB = RemoteServer(id = 2L, name = "Work", url = "https://work.example", secret = "s2")

    @Test
    fun `disabled preference or missing permission suppresses and closes notifications`() = runTest {
        withHarness(enabled = false) { harness ->
            harness.client.openConnect.emit(openConnectPending("oc", "c1"))
            runCurrent()
            assertTrue(harness.notifications.shown.isEmpty())

            harness.enabled.value = true
            harness.notifications.setPermission(BrowserNotificationPermission.DENIED)
            harness.client.openConnect.emit(openConnectPending("oc", "c2"))
            runCurrent()
            assertTrue(harness.notifications.shown.isEmpty())

            harness.notifications.setPermission(BrowserNotificationPermission.GRANTED)
            harness.client.openConnect.emit(openConnectPending("oc", "c3"))
            runCurrent()
            assertEquals(1, harness.notifications.activeTags().size)

            harness.enabled.value = false
            runCurrent()
            assertTrue(harness.notifications.activeTags().isEmpty())
        }
    }

    @Test
    fun `initial snapshot treats taildrop unread files as baseline but notifies login and vpn auth`() = runTest {
        withHarness { harness ->
            harness.notifications.pageFocused = false
            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts-files",
                            backendState = TAILSCALE_STATE_RUNNING,
                            unreadFileCount = 4,
                        ),
                        TailscaleEndpointStatus(
                            endpointTag = "ts-login",
                            backendState = TAILSCALE_STATE_NEEDS_LOGIN,
                            authURL = "https://login.example/a",
                            unreadFileCount = 2,
                        ),
                    ),
                ),
            )
            harness.client.openConnect.emit(openConnectPending("oc", "oc-1"))
            harness.client.openVPN.emit(openVPNPending("ovpn", "ovpn-1"))
            runCurrent()

            val active = harness.notifications.activeNotifications()
            assertEquals(3, active.size)
            assertEquals(
                setOf(
                    "husi-tailscale-login:1:ts-login",
                    "husi-openconnect:1:oc",
                    "husi-openvpn:1:ovpn",
                ),
                active.map { it.tag }.toSet(),
            )
        }
    }

    @Test
    fun `taildrop and tailscale login notifications handle clicks and auto-close on resolution`() = runTest {
        withHarness { harness ->
            val actions = mutableListOf<RemoteNotificationAction>()
            val job = backgroundScope.launch {
                harness.watcher.clickActions.collect { actions += it }
            }

            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts",
                            backendState = TAILSCALE_STATE_RUNNING,
                            unreadFileCount = 1,
                        ),
                    ),
                ),
            )
            runCurrent()
            assertTrue(harness.notifications.shown.isEmpty())

            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts",
                            backendState = TAILSCALE_STATE_NEEDS_LOGIN,
                            authURL = "https://login.example/1",
                            unreadFileCount = 3,
                        ),
                    ),
                ),
            )
            runCurrent()

            val taildrop = harness.notifications.activeNotifications().first { it.tag == "husi-taildrop:1:ts" }
            assertEquals("Tailscale: ts", taildrop.title)
            assertEquals("Received 2 files", taildrop.body)
            assertEquals("icons/icon-192.png", taildrop.icon)

            val login = harness.notifications.activeNotifications().first { it.tag == "husi-tailscale-login:1:ts" }
            assertEquals("Tailscale: ts", login.title)
            assertEquals("Waiting for login", login.body)

            harness.notifications.click("husi-taildrop:1:ts")
            runCurrent()
            assertEquals(listOf<RemoteNotificationAction>(RemoteNotificationAction.OpenTaildropInbox("ts")), actions)

            // Resolving login and marking inbox read closes remaining notification.
            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts",
                            backendState = TAILSCALE_STATE_RUNNING,
                            unreadFileCount = 0,
                        ),
                    ),
                ),
            )
            runCurrent()
            assertTrue(harness.notifications.activeTags().isEmpty())
            job.cancel()
        }
    }

    @Test
    fun `foreground focus filters openconnect openvpn and matching tailscale or inbox routes`() = runTest {
        withHarness { harness ->
            harness.notifications.pageFocused = true
            harness.watcher.currentRoute = RemoteRoute.Stats

            // Baseline tailscale endpoint.
            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(endpointTag = "ts", backendState = TAILSCALE_STATE_RUNNING, unreadFileCount = 0),
                    ),
                ),
            )
            runCurrent()

            // Focused on Stats -> OpenConnect/OpenVPN skip, Taildrop/NeedsLogin notify.
            harness.client.openConnect.emit(openConnectPending("oc", "c1"))
            harness.client.openVPN.emit(openVPNPending("ovpn", "v1"))
            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts",
                            backendState = TAILSCALE_STATE_NEEDS_LOGIN,
                            authURL = "https://login.example/a",
                            unreadFileCount = 1,
                        ),
                    ),
                ),
            )
            runCurrent()
            assertEquals(
                setOf("husi-tailscale-login:1:ts", "husi-taildrop:1:ts"),
                harness.notifications.activeTags().toSet(),
            )

            // Clear existing notifications.
            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts",
                            backendState = TAILSCALE_STATE_RUNNING,
                            unreadFileCount = 0,
                        ),
                    ),
                ),
            )
            runCurrent()
            assertTrue(harness.notifications.activeTags().isEmpty())

            // Focused on matching Inbox(ts) -> Taildrop skips, NeedsLogin still notifies.
            harness.watcher.currentRoute = RemoteRoute.Inbox("ts")
            val countBeforeInbox = harness.notifications.shown.size
            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts",
                            backendState = TAILSCALE_STATE_NEEDS_LOGIN,
                            authURL = "https://login.example/b",
                            unreadFileCount = 2,
                        ),
                    ),
                ),
            )
            runCurrent()
            assertEquals(countBeforeInbox + 1, harness.notifications.shown.size)
            assertEquals(
                listOf("husi-tailscale-login:1:ts"),
                harness.notifications.activeTags(),
            )

            // Clear again before checking Endpoint(ts).
            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts",
                            backendState = TAILSCALE_STATE_RUNNING,
                            unreadFileCount = 0,
                        ),
                    ),
                ),
            )
            runCurrent()
            assertTrue(harness.notifications.activeTags().isEmpty())

            // Focused on matching Endpoint(ts) -> NeedsLogin skips, Taildrop still notifies.
            harness.watcher.currentRoute = RemoteRoute.Endpoint("ts")
            val countBeforeEndpoint = harness.notifications.shown.size
            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts",
                            backendState = TAILSCALE_STATE_NEEDS_LOGIN,
                            authURL = "https://login.example/c",
                            unreadFileCount = 1,
                        ),
                    ),
                ),
            )
            runCurrent()
            assertEquals(countBeforeEndpoint + 1, harness.notifications.shown.size)
            assertEquals(
                listOf("husi-taildrop:1:ts"),
                harness.notifications.activeTags(),
            )
        }
    }

    @Test
    fun `openconnect and openvpn notify when unfocused and close when resolved`() = runTest {
        withHarness { harness ->
            harness.notifications.pageFocused = false
            val actions = mutableListOf<RemoteNotificationAction>()
            val job = backgroundScope.launch {
                harness.watcher.clickActions.collect { actions += it }
            }

            harness.client.openConnect.emit(openConnectPending("office", "oc-1"))
            harness.client.openVPN.emit(openVPNPending("corp", "ov-1"))
            runCurrent()

            val oc = harness.notifications.activeNotifications().first { it.tag == "husi-openconnect:1:office" }
            assertEquals("OpenConnect Authentication", oc.title)
            assertEquals("Authentication required: office", oc.body)

            val ovpn = harness.notifications.activeNotifications().first { it.tag == "husi-openvpn:1:corp" }
            assertEquals("OpenVPN Authentication", ovpn.title)
            assertEquals("Authentication required: corp", ovpn.body)

            harness.notifications.click("husi-openconnect:1:office")
            harness.notifications.click("husi-openvpn:1:corp")
            runCurrent()
            assertEquals(
                listOf(
                    RemoteNotificationAction.OpenOpenConnectAuth("office", "oc-1"),
                    RemoteNotificationAction.OpenOpenVPNAuth("corp", "ov-1"),
                ),
                actions,
            )

            // Re-emit new challenges and then resolve them -> notifications auto-close.
            harness.client.openConnect.emit(openConnectPending("office", "oc-2"))
            harness.client.openVPN.emit(openVPNPending("corp", "ov-2"))
            runCurrent()
            assertEquals(2, harness.notifications.activeTags().size)

            harness.client.openConnect.emit(
                OpenConnectStatusUpdate(
                    endpoints = listOf(OpenConnectEndpointStatus(endpointTag = "office", state = OPENCONNECT_STATE_CONNECTED)),
                ),
            )
            harness.client.openVPN.emit(
                OpenVPNStatusUpdate(
                    endpoints = listOf(OpenVPNEndpointStatus(endpointTag = "corp", state = OPENVPN_STATE_CONNECTED)),
                ),
            )
            runCurrent()
            assertTrue(harness.notifications.activeTags().isEmpty())
            job.cancel()
        }
    }

    @Test
    fun `multi-server setup appends server name only when multiple servers exist and name is non-blank`() = runTest {
        withHarness { harness ->
            harness.remote.save(serverB)
            runCurrent()
            harness.client.openConnect.emit(openConnectPending("office", "c1"))
            runCurrent()
            assertEquals("OpenConnect Authentication · Home", harness.notifications.shown.last().title)

            // Blank server name omits suffix even with multiple servers.
            val unnamed = harness.remote.save(RemoteServer(name = "   ", url = "https://unnamed.example"))
            harness.remote.connect(unnamed)
            runCurrent()
            harness.client.openConnect.emit(openConnectPending("office", "c2"))
            runCurrent()
            assertEquals("OpenConnect Authentication", harness.notifications.shown.last().title)
        }
    }

    @Test
    fun `transient same-server reconnect preserves dedup and dismissed dialog state`() = runTest {
        withHarness { harness ->
            harness.client.openConnect.emit(openConnectPending("office", "c1"))
            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts",
                            backendState = TAILSCALE_STATE_NEEDS_LOGIN,
                            authURL = "https://login.example/a",
                            unreadFileCount = 2,
                        ),
                    ),
                ),
            )
            runCurrent()
            assertEquals(2, harness.notifications.shown.size)
            assertNotNull(harness.remote.openConnectActions.pendingDialogAuth.value)

            // User clicks Later -> dialog hidden while challenge remains pending.
            harness.remote.openConnectActions.dismissDialog("office", "c1")
            assertNull(harness.remote.openConnectActions.pendingDialogAuth.value)

            // Simulate heartbeat failure and reconnect to the same server.
            harness.client.heartbeatFailure = true
            advanceTimeBy(5000.milliseconds)
            runCurrent()
            assertNull(harness.remote.openConnectActions.pendingDialogAuth.value)

            harness.client.heartbeatFailure = false
            advanceTimeBy(1000.milliseconds)
            runCurrent()
            assertTrue(harness.remote.connected)

            // Server re-emits the exact same pending challenge, login URL, and unread count -> no duplicate notifications or dialog.
            harness.client.openConnect.emit(openConnectPending("office", "c1"))
            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts",
                            backendState = TAILSCALE_STATE_NEEDS_LOGIN,
                            authURL = "https://login.example/a",
                            unreadFileCount = 2,
                        ),
                    ),
                ),
            )
            runCurrent()
            assertEquals(2, harness.notifications.shown.size)
            assertNull(harness.remote.openConnectActions.pendingDialogAuth.value)

            // New unread files arriving while/after reconnect notify only for the increase.
            harness.client.tailscale.emit(
                TailscaleStatusUpdate(
                    endpoints = listOf(
                        TailscaleEndpointStatus(
                            endpointTag = "ts",
                            backendState = TAILSCALE_STATE_RUNNING,
                            unreadFileCount = 5,
                        ),
                    ),
                ),
            )
            runCurrent()
            assertEquals(3, harness.notifications.shown.size)
            assertEquals("Received 3 files", harness.notifications.shown.last().body)
            // Login notification was auto-closed on transition to Running.
            assertEquals(listOf("husi-openconnect:1:office", "husi-taildrop:1:ts"), harness.notifications.activeTags())
        }
    }

    @Test
    fun `switching servers or disconnecting resets dedup dismissed dialogs and closes active notifications`() = runTest {
        withHarness { harness ->
            harness.remote.save(serverB)
            harness.client.openConnect.emit(openConnectPending("office", "c1"))
            runCurrent()
            harness.remote.openConnectActions.dismissDialog("office", "c1")
            assertEquals(listOf("husi-openconnect:1:office"), harness.notifications.activeTags())

            // Switch to server B -> old notification closes, same challenge id notifies again & opens dialog.
            harness.remote.connect(serverB)
            runCurrent()
            assertTrue(harness.notifications.activeTags().isEmpty())

            harness.client.openConnect.emit(openConnectPending("office", "c1"))
            runCurrent()
            assertEquals(listOf("husi-openconnect:2:office"), harness.notifications.activeTags())
            assertEquals("c1", harness.remote.openConnectActions.pendingDialogAuth.value?.challenge?.id)

            // Disconnect -> closes all active notifications.
            harness.remote.disconnect()
            runCurrent()
            assertTrue(harness.notifications.activeTags().isEmpty())
        }
    }

    private suspend fun TestScope.withHarness(
        enabled: Boolean = true,
        block: suspend (Harness) -> Unit,
    ) {
        val client = RecordingClient()
        val store = FakeRemoteServerStore()
        val saved = store.save(serverA)
        val activeServerId = FakePreferencesDataStore().preferences.long(Key.ACTIVE_REMOTE_SERVER_ID)
        activeServerId.set(saved.id)
        val remoteScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val remote = RemoteState(
            scope = remoteScope,
            client = client,
            serverStore = store,
            speedInterval = flowOf(1000),
            activeServerId = activeServerId,
        )
        val notifications = FakeBrowserNotificationClient(BrowserNotificationPermission.GRANTED)
        val enabledFlow = MutableStateFlow(enabled)
        val watcher = RemoteNotificationWatcher(
            scope = remoteScope,
            remote = remote,
            notifications = notifications,
            repository = StubRepository(),
            enabledFlow = enabledFlow,
        )
        runCurrent()
        try {
            block(Harness(remote, client, notifications, watcher, enabledFlow))
        } finally {
            watcher.close()
            remote.close()
        }
    }

    private class Harness(
        val remote: RemoteState,
        val client: RecordingClient,
        val notifications: FakeBrowserNotificationClient,
        val watcher: RemoteNotificationWatcher,
        val enabled: MutableStateFlow<Boolean>,
    )

    private fun openConnectPending(tag: String, challengeId: String) = OpenConnectStatusUpdate(
        endpoints = listOf(
            OpenConnectEndpointStatus(
                endpointTag = tag,
                state = OPENCONNECT_STATE_AUTH_PENDING,
                authChallenge = OpenConnectAuthChallenge(id = challengeId),
            ),
        ),
    )

    private fun openVPNPending(tag: String, challengeId: String) = OpenVPNStatusUpdate(
        endpoints = listOf(
            OpenVPNEndpointStatus(
                endpointTag = tag,
                state = OPENVPN_STATE_AUTH_PENDING,
                challenge = OpenVPNChallenge(
                    id = challengeId,
                    kind = OPENVPN_CHALLENGE_CREDENTIALS,
                ),
            ),
        ),
    )

    private class RecordingClient : FakeRemoteClient() {
        val openConnect = MutableSharedFlow<OpenConnectStatusUpdate>()
        val openVPN = MutableSharedFlow<OpenVPNStatusUpdate>()
        val tailscale = MutableSharedFlow<TailscaleStatusUpdate>()
        var heartbeatFailure = false

        override suspend fun connect(url: String, secret: String) = Version(version = "1.0.0")

        override suspend fun call(method: String, request: ByteArray): ByteArray {
            check(method != "GetVersion" || !heartbeatFailure) { "Disconnected" }
            return byteArrayOf()
        }

        override fun stream(method: String, request: ByteArray): Flow<ByteArray> = flow {
            when (method) {
                "SubscribeOpenConnectStatus" -> openConnect.collect { emit(it.encode()) }
                "SubscribeOpenVPNStatus" -> openVPN.collect { emit(it.encode()) }
                "SubscribeTailscaleStatus" -> tailscale.collect { emit(it.encode()) }
                else -> awaitCancellation()
            }
        }
    }

    private class StubRepository : Repository {
        override suspend fun getString(resource: StringResource): String = when (resource) {
            Res.string.openconnect_authentication -> "OpenConnect Authentication"
            Res.string.openvpn_authentication -> "OpenVPN Authentication"
            Res.string.auth_required -> "Authentication required"
            Res.string.tailscale_state_needs_login -> "Waiting for login"
            else -> resource.toString()
        }

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
            when (resource) {
                Res.string.tailscale_with_tag -> "Tailscale: ${formatArgs.first()}"
                else -> "$resource:${formatArgs.joinToString()}"
            }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String = when (resource) {
            Res.plurals.taildrop_received_notification -> if (quantity == 1) {
                "Received 1 file"
            } else {
                "Received ${formatArgs.first()} files"
            }

            else -> "$resource:$quantity"
        }
    }
}
