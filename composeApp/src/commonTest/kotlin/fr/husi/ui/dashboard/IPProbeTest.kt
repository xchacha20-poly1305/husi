package fr.husi.ui.dashboard

import fr.husi.database.DataStore
import fr.husi.test.FakeCoreClient
import fr.husi.test.HusiKoinMainDispatcherTest
import fr.husi.ui.StringOrRes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IPProbeTest : HusiKoinMainDispatcherTest() {
    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    private fun newViewModel(client: FakeCoreClient) = DashboardViewModel(
        loadPlatformNetworkInfo = { Triple(emptyList(), null, null) },
        coreClient = client,
    )

    private suspend fun configureAutoDetection(enabled: Boolean = true, visible: Boolean = true) {
        DataStore.autoDetectIP.set(enabled)
        DataStore.ipTestServers.set("stun:one.example")
        DataStore.dashboardWidgets.set(
            encodeDashboardWidgets(defaultDashboardWidgets().setWidgetVisible(DashboardWidget.ExitIP, visible)),
        )
    }

    private class CountingCoreClient : FakeCoreClient() {
        var detections = 0

        override suspend fun detectIP(server: String): String {
            detections++
            return super.detectIP(server)
        }
    }

    @Test
    fun `entering dashboard detects once and reentry detects again`() = runTest(dispatcher.scheduler) {
        configureAutoDetection()
        val client = CountingCoreClient()
        val model = newViewModel(client)
        model.setDashboardActive(true)
        model.initialize(false)
        advanceUntilIdle()
        assertEquals(1, client.detections)
        model.setDashboardActive(true)
        advanceUntilIdle()
        assertEquals(1, client.detections)
        model.setDashboardActive(false)
        model.setDashboardActive(true)
        advanceUntilIdle()
        assertEquals(2, client.detections)
    }

    @Test
    fun `starting instance detects again but stopping does not`() = runTest(dispatcher.scheduler) {
        configureAutoDetection()
        val client = CountingCoreClient()
        val model = newViewModel(client)
        model.initialize(false)
        model.setDashboardActive(true)
        advanceUntilIdle()
        assertEquals(1, client.detections)
        model.initialize(true)
        advanceUntilIdle()
        assertEquals(2, client.detections)
        model.initialize(false)
        advanceUntilIdle()
        assertEquals(2, client.detections)
    }

    @Test
    fun `starting instance replaces pending disconnected detection`() = runTest(dispatcher.scheduler) {
        configureAutoDetection()
        val pending = CompletableDeferred<String>()
        var calls = 0
        val client = object : FakeCoreClient() {
            override suspend fun detectIP(server: String): String {
                calls++
                return if (calls == 1) pending.await() else super.detectIP(server)
            }
        }
        val model = newViewModel(client)
        model.setDashboardActive(true)
        model.initialize(false)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(model.uiState.value.ipProbe.isDoing)
        model.initialize(true)
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(client.detectedIP, model.uiState.value.ipProbe.ip)
        assertFalse(model.uiState.value.ipProbe.isDoing)
    }

    @Test
    fun `hidden IP card prevents automatic detection including during initialization`() = runTest(dispatcher.scheduler) {
        configureAutoDetection(visible = false)
        val client = CountingCoreClient()
        val model = newViewModel(client)
        model.setDashboardActive(true)
        model.initialize(false)
        advanceUntilIdle()
        model.initialize(true)
        advanceUntilIdle()
        assertEquals(0, client.detections)
    }

    @Test
    fun `disabled setting prevents automatic detection`() = runTest(dispatcher.scheduler) {
        configureAutoDetection(enabled = false)
        val client = CountingCoreClient()
        val model = newViewModel(client)
        model.setDashboardActive(true)
        model.initialize(false)
        advanceUntilIdle()
        model.initialize(true)
        advanceUntilIdle()
        assertEquals(0, client.detections)
    }

    @Test
    fun `starting instance outside dashboard waits until entry to detect`() = runTest(dispatcher.scheduler) {
        configureAutoDetection()
        val client = CountingCoreClient()
        val model = newViewModel(client)
        model.initialize(false)
        model.initialize(true)
        advanceUntilIdle()
        assertEquals(0, client.detections)
        model.setDashboardActive(true)
        advanceUntilIdle()
        assertEquals(1, client.detections)
    }

    @Test
    fun `configured server is selected and result includes country`() = runTest(dispatcher.scheduler) {
        DataStore.ipTestServers.set("\n stun:one.example \n\nturns:two.example\n")
        val client = FakeCoreClient().also { it.detectedCountry = "JP" }
        val model = newViewModel(client)
        model.initialize(true)
        model.detectIP()
        advanceUntilIdle()
        assertTrue(client.lastIPServer in listOf("stun:one.example", "turns:two.example"))
        assertEquals("203.0.113.1", model.uiState.value.ipProbe.ip)
        assertEquals("JP", model.uiState.value.ipProbe.countryCode)
        assertFalse(model.uiState.value.ipProbe.isDoing)
    }

    @Test
    fun `empty configuration does not send requests`() = runTest(dispatcher.scheduler) {
        DataStore.ipTestServers.set(" \n ")
        val client = FakeCoreClient()
        val model = newViewModel(client)
        model.initialize(true)
        model.detectIP()
        advanceUntilIdle()
        assertIs<StringOrRes.Res>(model.uiState.value.ipProbe.error)
        assertNull(client.lastIPServer)
    }

    @Test
    fun `stopped service can query exit IP`() = runTest(dispatcher.scheduler) {
        DataStore.ipTestServers.set("stun:one.example")
        val client = FakeCoreClient().also { it.detectedCountry = "JP" }
        val model = newViewModel(client)
        model.initialize(false)
        model.detectIP()
        advanceUntilIdle()
        assertEquals("stun:one.example", client.lastIPServer)
        assertEquals(client.detectedIP, model.uiState.value.ipProbe.ip)
        assertEquals("JP", model.uiState.value.ipProbe.countryCode)
        assertFalse(model.uiState.value.ipProbe.isDoing)
    }

    @Test
    fun `repeated click sends one request and disconnect cancels it`() = runTest(dispatcher.scheduler) {
        DataStore.ipTestServers.set("stun:one.example")
        val pending = CompletableDeferred<String>()
        var calls = 0
        val client = object : FakeCoreClient() {
            override suspend fun detectIP(server: String): String {
                calls++
                return pending.await()
            }
        }
        val model = newViewModel(client)
        model.initialize(true)
        model.detectIP()
        advanceUntilIdle()
        model.detectIP()
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(model.uiState.value.ipProbe.isDoing)
        model.initialize(false)
        pending.complete("203.0.113.1")
        advanceUntilIdle()
        assertEquals(IPProbeState(), model.uiState.value.ipProbe)
        model.detectIP()
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals("203.0.113.1", model.uiState.value.ipProbe.ip)
    }

    @Test
    fun `lookup failure preserves detected IP`() = runTest(dispatcher.scheduler) {
        DataStore.ipTestServers.set("stun:one.example")
        val client = object : FakeCoreClient() {
            override suspend fun lookupIPCountry(dir: File, ip: String): String = error("GeoIP unavailable")
        }
        val model = newViewModel(client)
        model.initialize(true)
        model.detectIP()
        advanceUntilIdle()
        assertEquals(client.detectedIP, model.uiState.value.ipProbe.ip)
        assertIs<StringOrRes.Direct>(model.uiState.value.ipProbe.error)
        assertFalse(model.uiState.value.ipProbe.isDoing)
    }

    @Test
    fun `country flag accepts alpha two codes`() {
        assertEquals("🇨🇳", countryEmoji("cn"))
        assertEquals("🇯🇵", countryEmoji("JP"))
        assertNull(countryEmoji(""))
        assertNull(countryEmoji("private"))
    }
}
