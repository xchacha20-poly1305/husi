package fr.husi.repository

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacOsSystemProxyBackendTest {

    @Test
    fun `switching active services batches cleanup and all three new proxy settings`() {
        var activeDevices = setOf("en0", "utun3")
        val preferences = FakeProxyPreferences()
        val backend = MacOsSystemProxyBackend(preferences) { activeDevices }

        backend.enable("127.0.0.1", 2080)
        assertEquals(setOf("wifi"), preferences.commits.single().keys)
        assertEquals(enabled, preferences.services.getValue("wifi").values)

        activeDevices = setOf("en5")
        preferences.services["wifi"] = preferences.services.getValue("wifi").copy(inCurrentSet = false)
        backend.enable("127.0.0.1", 2080)
        assertEquals(mapOf("wifi" to disabled, "ethernet" to enabled), preferences.commits.last())
        assertEquals(2, preferences.commits.size)

        backend.disable()
        assertEquals(mapOf("ethernet" to disabled), preferences.commits.last())
        assertFalse(backend.needsCleanup)
    }

    @Test
    fun `unmatched interface falls back to enabled proxy capable services in the current location`() {
        val preferences = FakeProxyPreferences()
        val backend = MacOsSystemProxyBackend(preferences) { setOf("utun3") }

        backend.enable("127.0.0.1", 2080)

        assertEquals(setOf("wifi", "ethernet"), preferences.commits.single().keys)
    }

    @Test
    fun `apply failure after commit leaves the service tracked for cleanup`() {
        val preferences = FakeProxyPreferences()
        val backend = MacOsSystemProxyBackend(preferences) { setOf("en0") }
        preferences.failApply = true

        assertFailsWith<IllegalStateException> { backend.enable("127.0.0.1", 2080) }
        assertTrue(backend.needsCleanup)

        assertFailsWith<IllegalStateException> { backend.disable() }
        assertTrue(backend.needsCleanup)

        preferences.failApply = false
        backend.disable()
        assertFalse(backend.needsCleanup)
        assertEquals(2, preferences.commits.size)
        assertEquals(3, preferences.applyAttempts)
    }

    @Test
    fun `hasRoutableAddress rejects unusable addresses and accepts usable IPv4 and IPv6`() {
        val nonRoutable = listOf("127.0.0.1", "169.254.1.2", "fe80::1", "::", "224.0.0.1")
            .map(InetAddress::getByName)
        assertFalse(hasRoutableAddress(nonRoutable))
        assertTrue(hasRoutableAddress(listOf(InetAddress.getByName("192.168.1.10"))))
        assertTrue(hasRoutableAddress(listOf(InetAddress.getByName("2001:db8::1"))))
    }

    private class FakeProxyPreferences : MacOsProxyPreferences {
        val services = mutableMapOf(
            "wifi" to MacOsProxyService("en0", enabled = true, inCurrentSet = true, values = emptyMap()),
            "ethernet" to MacOsProxyService("en5", enabled = true, inCurrentSet = true, values = emptyMap()),
            "disabled" to MacOsProxyService("en7", enabled = false, inCurrentSet = true, values = emptyMap()),
            "otherLocation" to MacOsProxyService("en8", enabled = true, inCurrentSet = false, values = emptyMap()),
        )
        val commits = mutableListOf<Map<String, Map<String, Any>>>()
        var applyAttempts = 0
        var failApply = false

        override fun update(
            trackedServices: Set<String>,
            applyPending: Boolean,
            changes: (Map<String, MacOsProxyService>) -> Map<String, Map<String, Any>>,
            committed: (Boolean) -> Unit,
        ) {
            val updates = changes(services.filter { (id, service) -> service.inCurrentSet || id in trackedServices })
            if (updates.isNotEmpty()) {
                for ((id, fields) in updates) {
                    val service = services.getValue(id)
                    services[id] = service.copy(values = service.values + fields)
                }
                commits += updates
            }
            val needsApply = updates.isNotEmpty() || applyPending
            committed(needsApply)
            if (needsApply) {
                applyAttempts++
                check(!failApply) { "apply failed" }
            }
        }
    }

    private companion object {
        val enabled = listOf("HTTP", "HTTPS", "SOCKS").flatMap {
            listOf("${it}Enable" to 1, "${it}Proxy" to "127.0.0.1", "${it}Port" to 2080)
        }.toMap()
        val disabled = listOf("HTTP", "HTTPS", "SOCKS").associate { "${it}Enable" to 0 }
    }
}
