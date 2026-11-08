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
    fun `remote routes round trip with the serializers used by the saved back stack`() {
        val json = Json { serializersModule = RemoteRoute.savedStateConfiguration.serializersModule }
        val serializer = PolymorphicSerializer(NavKey::class)
        val routes = listOf(
            RemoteRoute.Setup,
            RemoteRoute.Dashboard, RemoteRoute.Logs, RemoteRoute.Tools, RemoteRoute.Servers,
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
            listOf(RemoteRoute.Dashboard),
            listOf(RemoteRoute.Tools, RemoteRoute.Stun),
            listOf(RemoteRoute.Servers, RemoteRoute.ServerEdit(1L)),
        )) {
            val routes = remoteBackStack(stack, hasServer = false)
            assertEquals(listOf(RemoteRoute.Setup), routes)
            assertFalse(Navigator(routes.toMutableList(), RemoteRoute.topLevel).popBackStack())
        }
    }

    @Test
    fun `selecting a server leaves setup with no back route to it`() {
        val routes = remoteBackStack(listOf(RemoteRoute.Setup), hasServer = true)
        assertEquals(listOf(RemoteRoute.Dashboard), routes)
        assertFalse(Navigator(routes.toMutableList(), RemoteRoute.topLevel).popBackStack())
        val tools = listOf(RemoteRoute.Tools, RemoteRoute.Stun)
        assertEquals(tools, remoteBackStack(tools, hasServer = true))
    }

    @Test
    fun `the shared navigator returns from a remote subpage to its top level destination`() {
        val stack = mutableListOf<NavKey>(RemoteRoute.Dashboard)
        val navigator = Navigator(stack, RemoteRoute.topLevel)
        navigator.navigateTo(RemoteRoute.Endpoint("tailscale"))
        assertFalse(navigator.isCurrentTopLevel)
        assertEquals(RemoteRoute.Dashboard, navigator.selectedTopLevelRoute)
        assertTrue(navigator.popBackStack())
        navigator.navigateToTopLevelRoute(RemoteRoute.Logs)
        assertEquals(RemoteRoute.Logs, navigator.currentRoute)
        assertTrue(navigator.isCurrentTopLevel)
    }
}
