package fr.husi.ui

import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json

class RemoteRouteTest {
    @Test
    fun `destinations follow the active controller protocol`() {
        assertTrue(remoteRouteAvailable(RemoteRoute.Rules, true))
        assertFalse(remoteRouteAvailable(RemoteRoute.Rules, false))
        assertTrue(remoteRouteAvailable(RemoteRoute.Stun, false))
        assertFalse(remoteRouteAvailable(RemoteRoute.Stun, true))
        assertFalse(remoteRouteAvailable(RemoteRoute.Peer("tag", "peer"), true))
        assertTrue(remoteRouteAvailable(RemoteRoute.Settings, true))
    }

    @Test
    fun `remote routes round trip with the serializers used by the saved back stack`() {
        val json = Json {
            serializersModule = RemoteRoute.savedStateConfiguration.serializersModule
        }
        val serializer = PolymorphicSerializer(NavKey::class)
        val routes = listOf(
            RemoteRoute.Setup,
            RemoteRoute.Stats, RemoteRoute.ProxySet, RemoteRoute.Connections, RemoteRoute.Logs,
            RemoteRoute.Tools, RemoteRoute.Servers, RemoteRoute.GeneralSettings,
            RemoteRoute.Rules, RemoteRoute.Providers, RemoteRoute.Dns,
            RemoteRoute.Settings, RemoteRoute.About, RemoteRoute.Libraries,
            RemoteRoute.ServerEdit(1L), RemoteRoute.Stun, RemoteRoute.NetworkQuality,
            RemoteRoute.Endpoint("tailscale"), RemoteRoute.Peer("tailscale", "peer"),
            RemoteRoute.ExitNode("tailscale"), RemoteRoute.Inbox("tailscale"), RemoteRoute.Ssh,
        )
        for (route in routes) {
            assertEquals(route, json.decodeFromString(serializer, json.encodeToString(serializer, route)))
        }
    }

    @Test
    fun `missing server replaces empty and restored navigation with setup`() {
        for (stack in listOf(
            emptyList(),
            listOf(RemoteRoute.Stats),
            listOf(RemoteRoute.Settings, RemoteRoute.Tools, RemoteRoute.Stun),
            listOf(RemoteRoute.Settings, RemoteRoute.Servers, RemoteRoute.ServerEdit(1L)),
        )) {
            val routes = remoteBackStack(stack, hasServer = false)
            assertEquals(listOf(RemoteRoute.Setup), routes)
            assertFalse(Navigator(routes.toMutableList(), RemoteRoute.topLevel).popBackStack())
        }
    }

    @Test
    fun `selecting a server leaves setup with no back route to it`() {
        val routes = remoteBackStack(listOf(RemoteRoute.Setup), hasServer = true)
        assertEquals(listOf(RemoteRoute.Stats), routes)
        assertFalse(Navigator(routes.toMutableList(), RemoteRoute.topLevel).popBackStack())
        val tools = listOf(RemoteRoute.Settings, RemoteRoute.Tools, RemoteRoute.Stun)
        assertEquals(tools, remoteBackStack(tools, hasServer = true))
    }

    @Test
    fun `the shared navigator returns from a remote subpage to its top level destination`() {
        val stack = mutableListOf<NavKey>(RemoteRoute.Stats)
        val navigator = Navigator(stack, RemoteRoute.topLevel)
        navigator.navigateTo(RemoteRoute.Endpoint("tailscale"))
        assertFalse(navigator.isCurrentTopLevel)
        assertEquals(RemoteRoute.Stats, navigator.selectedTopLevelRoute)
        assertTrue(navigator.popBackStack())
        navigator.navigateToTopLevelRoute(RemoteRoute.Logs)
        assertEquals(RemoteRoute.Logs, navigator.currentRoute)
        assertTrue(navigator.isCurrentTopLevel)
    }

    @Test
    fun `settings subpages keep settings selected and return to the hub`() {
        for (destination in listOf(
            RemoteRoute.GeneralSettings,
            RemoteRoute.Tools,
            RemoteRoute.Servers,
        )) {
            val stack = mutableListOf<NavKey>(RemoteRoute.Stats)
            val navigator = Navigator(stack, RemoteRoute.topLevel)
            navigator.navigateToTopLevelRoute(RemoteRoute.Settings)
            navigator.navigateTo(destination)
            assertFalse(navigator.isCurrentTopLevel)
            assertEquals(RemoteRoute.Settings, navigator.selectedTopLevelRoute)
            assertTrue(navigator.popBackStack())
            assertEquals(RemoteRoute.Settings, navigator.currentRoute)
        }
    }
}
