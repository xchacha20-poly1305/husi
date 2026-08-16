package fr.husi.cli

import fr.husi.core.CoreRpcException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiVpnCommandsTest {
    @Test
    fun `resolveVpnEndpoint returns the explicit tag when present`() {
        assertEquals("beta", resolveVpnEndpoint(listOf("alpha", "beta"), "beta", "openvpn") { it })
    }

    @Test
    fun `resolveVpnEndpoint throws when the explicit tag is missing`() {
        val error = assertFailsWith<VpnCliException> {
            resolveVpnEndpoint(listOf("alpha"), "x", "openvpn") { it }
        }
        assertEquals("endpoint not found: x", error.message)
    }

    @Test
    fun `resolveVpnEndpoint throws when no endpoint is configured`() {
        val error = assertFailsWith<VpnCliException> {
            resolveVpnEndpoint(emptyList<String>(), null, "openvpn") { it }
        }
        assertEquals("no openvpn endpoint is configured", error.message)
    }

    @Test
    fun `resolveVpnEndpoint implies the single configured endpoint`() {
        assertEquals("only", resolveVpnEndpoint(listOf("only"), null, "openvpn") { it })
    }

    @Test
    fun `resolveVpnEndpoint requires --endpoint when several endpoints exist`() {
        val error = assertFailsWith<VpnCliException> {
            resolveVpnEndpoint(listOf("alpha", "beta"), null, "openvpn") { it }
        }
        assertContains(error.message, "alpha")
        assertContains(error.message, "beta")
        assertContains(error.message, "--endpoint")
    }

    @Test
    fun `classifyVpnSubmitError maps fatal stale and rejected outcomes`() {
        assertEquals(
            VpnSubmitOutcome.FATAL,
            classifyVpnSubmitError(CoreRpcException("Unavailable", "connection lost")),
        )
        assertEquals(
            VpnSubmitOutcome.STALE,
            classifyVpnSubmitError(CoreRpcException("InvalidArgument", "no pending challenge")),
        )
        assertEquals(
            VpnSubmitOutcome.REJECTED,
            classifyVpnSubmitError(CoreRpcException("InvalidArgument", "wrong password")),
        )
    }

    @Test
    fun `deadline and connected since treat zero as unset`() {
        assertEquals("", formatVpnRemainingSuffix(0L))
        assertEquals("", formatVpnConnectedSince(0L))
    }

    @Test
    fun `a passed deadline reads as zero rather than as a negative duration`() {
        val longGone = System.currentTimeMillis() / 1000 - 60
        assertEquals(" (0s remaining)", formatVpnRemainingSuffix(longGone))
    }
}
