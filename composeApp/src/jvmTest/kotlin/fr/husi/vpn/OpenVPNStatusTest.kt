package fr.husi.vpn

import fr.husi.proto.daemon.openVPNChallenge
import fr.husi.proto.daemon.openVPNEndpointStatus
import fr.husi.proto.daemon.openVPNTunnelInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenVPNStatusTest {

    @Test
    fun `credentials and secret challenges are answerable`() {
        assertTrue(challenge(kind = OPENVPN_CHALLENGE_CREDENTIALS).answerable)
        assertTrue(challenge(kind = OPENVPN_CHALLENGE_SECRET).answerable)
        assertFalse(challenge(kind = OPENVPN_CHALLENGE_MESSAGE).answerable)
        assertFalse(challenge(kind = OPENVPN_CHALLENGE_OPEN_URL).answerable)
    }

    @Test
    fun `formatOpenVPNRemaining hides challenges without a deadline`() {
        assertNull(formatOpenVPNRemaining(0, nowEpochSeconds = 100))
    }

    @Test
    fun `formatOpenVPNRemaining formats remaining minutes and seconds`() {
        assertEquals("1:05", formatOpenVPNRemaining(deadline = 185, nowEpochSeconds = 120))
        assertEquals("0:00", formatOpenVPNRemaining(deadline = 50, nowEpochSeconds = 80))
    }

    @Test
    fun `toState maps endpoint challenge and tunnel info`() {
        val status = openVPNEndpointStatus {
            endpointTag = "home"
            state = OPENVPN_STATE_AUTH_PENDING
            stateText = "Waiting"
            error = "retry"
            challenge = openVPNChallenge {
                id = "c1"
                kind = OPENVPN_CHALLENGE_CREDENTIALS
                username = "alice"
                message = "Sign in"
                url = "https://vpn.example/auth"
                secretMessage = "OTP"
                echo = true
                previousError = "bad password"
                deadline = 42
            }
            tunnelInfo = openVPNTunnelInfo {
                server = "vpn.example"
                network = "udp"
                cipher = "AES-256-GCM"
                ipv4 += "10.0.0.2"
                ipv6 += "fd00::2"
                dns += "1.1.1.1"
                mtu = 1400
                connectedSince = 99
            }
        }

        val state = status.toState()
        assertEquals("home", state.tag)
        assertEquals(OPENVPN_STATE_AUTH_PENDING, state.state)
        assertEquals("Waiting", state.stateText)
        assertEquals("retry", state.error)
        assertEquals(
            OpenVPNChallengeState(
                id = "c1",
                kind = OPENVPN_CHALLENGE_CREDENTIALS,
                username = "alice",
                message = "Sign in",
                url = "https://vpn.example/auth",
                secretMessage = "OTP",
                echo = true,
                previousError = "bad password",
                deadline = 42,
            ),
            state.challenge,
        )
        assertEquals(
            OpenVPNTunnelInfoState(
                server = "vpn.example",
                network = "udp",
                cipher = "AES-256-GCM",
                ipv4 = listOf("10.0.0.2"),
                ipv6 = listOf("fd00::2"),
                dns = listOf("1.1.1.1"),
                mtu = 1400,
                connectedSince = 99,
            ),
            state.tunnelInfo,
        )
    }

    private fun challenge(kind: String) = OpenVPNChallengeState(
        id = "c",
        kind = kind,
        username = "",
        message = "",
        url = "",
        secretMessage = "",
        echo = false,
        previousError = "",
        deadline = 0,
    )
}
