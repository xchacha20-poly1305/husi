package fr.husi.cli

import com.github.ajalt.clikt.core.ProgramResult
import fr.husi.proto.daemon.tailscaleEndpointStatus
import fr.husi.proto.daemon.tailscalePeer
import fr.husi.proto.daemon.tailscaleUserGroup
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiTailscaleCommandsTest {

    @Test
    fun `resolveTailscaleEndpointStatus succeeds with single endpoint`() {
        val endpoint = tailscaleEndpointStatus { endpointTag = "ts-main" }
        val resolved = resolveTailscaleEndpointStatus(listOf(endpoint), "")
        assertEquals("ts-main", resolved.endpointTag)
    }

    @Test
    fun `resolveTailscaleEndpointStatus succeeds with matching endpoint flag`() {
        val ep1 = tailscaleEndpointStatus { endpointTag = "ts-1" }
        val ep2 = tailscaleEndpointStatus { endpointTag = "ts-2" }
        val resolved = resolveTailscaleEndpointStatus(listOf(ep1, ep2), "ts-2")
        assertEquals("ts-2", resolved.endpointTag)
    }

    @Test
    fun `resolveTailscaleEndpointStatus throws on multiple endpoints when no flag given`() {
        val ep1 = tailscaleEndpointStatus { endpointTag = "ts-1" }
        val ep2 = tailscaleEndpointStatus { endpointTag = "ts-2" }
        assertFailsWith<ProgramResult> {
            resolveTailscaleEndpointStatus(listOf(ep1, ep2), "")
        }
    }

    @Test
    fun `resolveTailscaleEndpointStatus throws on unknown endpoint tag`() {
        val ep1 = tailscaleEndpointStatus { endpointTag = "ts-1" }
        assertFailsWith<ProgramResult> {
            resolveTailscaleEndpointStatus(listOf(ep1), "unknown")
        }
    }

    @Test
    fun `resolveTailscaleEndpointStatus throws on empty endpoints`() {
        assertFailsWith<ProgramResult> {
            resolveTailscaleEndpointStatus(emptyList(), "")
        }
    }

    @Test
    fun `tailscalePeerEntries puts self first followed by user group peers`() {
        val self = tailscalePeer {
            stableID = "self-id"
            hostName = "my-box"
        }
        val peer1 = tailscalePeer {
            stableID = "peer-1"
            hostName = "server"
        }
        val group = tailscaleUserGroup {
            peers.add(peer1)
        }
        val endpoint = tailscaleEndpointStatus {
            endpointTag = "ts"
            this.self = self
            userGroups.add(group)
        }

        val entries = tailscalePeerEntries(endpoint)
        assertEquals(2, entries.size)
        assertTrue(entries[0].self)
        assertEquals("self-id", entries[0].peer.stableID)
        assertEquals("peer-1", entries[1].peer.stableID)
    }

    @Test
    fun `tailscalePeerName falls back across dnsName hostName stableID`() {
        val withDns = tailscalePeer {
            dnsName = "node1.example.ts.net."
            hostName = "node1"
            stableID = "id1"
        }
        assertEquals("node1.example.ts.net", tailscalePeerName(withDns))

        val withHost = tailscalePeer {
            hostName = "node2"
            stableID = "id2"
        }
        assertEquals("node2", tailscalePeerName(withHost))

        val withIdOnly = tailscalePeer {
            stableID = "id3"
        }
        assertEquals("id3", tailscalePeerName(withIdOnly))
    }

    @Test
    fun `tailscalePeerAddress returns first IP or empty`() {
        val withIps = tailscalePeer {
            tailscaleIPs.addAll(listOf("100.64.0.1", "fd7a:115c:a1e0::1"))
        }
        assertEquals("100.64.0.1", tailscalePeerAddress(withIps))

        val withoutIps = tailscalePeer {}
        assertEquals("", tailscalePeerAddress(withoutIps))
    }

    @Test
    fun `resolveTailscalePeer matches by stableID`() {
        val p1 = TailscalePeerEntry(tailscalePeer {
            stableID = "stable-xyz"
            hostName = "host1"
        })
        val p2 = TailscalePeerEntry(tailscalePeer {
            stableID = "stable-abc"
            hostName = "host2"
        })
        val res = resolveTailscalePeer(listOf(p1, p2), "stable-xyz")
        assertNotNull(res.entry)
        assertNull(res.error)
        assertEquals("stable-xyz", res.entry.peer.stableID)
    }

    @Test
    fun `resolveTailscalePeer matches by IP address`() {
        val p1 = TailscalePeerEntry(tailscalePeer {
            stableID = "stable-1"
            tailscaleIPs.add("100.64.0.42")
        })
        val p2 = TailscalePeerEntry(tailscalePeer {
            stableID = "stable-2"
            tailscaleIPs.add("100.64.0.99")
        })
        val res = resolveTailscalePeer(listOf(p1, p2), "100.64.0.42")
        assertNotNull(res.entry)
        assertEquals("stable-1", res.entry.peer.stableID)
    }

    @Test
    fun `resolveTailscalePeer matches by DNS name or FQDN`() {
        val p1 = TailscalePeerEntry(tailscalePeer {
            stableID = "p1"
            dnsName = "my-nas.net.ts.net."
        })
        val res1 = resolveTailscalePeer(listOf(p1), "my-nas.net.ts.net")
        assertEquals("p1", res1.entry?.peer?.stableID)

        val res2 = resolveTailscalePeer(listOf(p1), "my-nas.net.ts.net.")
        assertEquals("p1", res2.entry?.peer?.stableID)
    }

    @Test
    fun `resolveTailscalePeer matches by first DNS label`() {
        val p1 = TailscalePeerEntry(tailscalePeer {
            stableID = "p1"
            dnsName = "workstation.example.ts.net"
        })
        val res = resolveTailscalePeer(listOf(p1), "workstation")
        assertEquals("p1", res.entry?.peer?.stableID)
    }

    @Test
    fun `resolveTailscalePeer matches by hostname`() {
        val p1 = TailscalePeerEntry(tailscalePeer {
            stableID = "p1"
            hostName = "raspberrypi"
        })
        val res = resolveTailscalePeer(listOf(p1), "raspberrypi")
        assertEquals("p1", res.entry?.peer?.stableID)
    }

    @Test
    fun `resolveTailscalePeer reports ambiguous error on multiple matches`() {
        val p1 = TailscalePeerEntry(tailscalePeer {
            stableID = "p1"
            hostName = "server"
            tailscaleIPs.add("100.64.0.1")
        })
        val p2 = TailscalePeerEntry(tailscalePeer {
            stableID = "p2"
            hostName = "server"
            tailscaleIPs.add("100.64.0.2")
        })
        val res = resolveTailscalePeer(listOf(p1, p2), "server")
        assertNull(res.entry)
        assertNotNull(res.error)
        assertContains(res.error, "ambiguous peer: server")
        assertContains(res.error, "p1")
        assertContains(res.error, "p2")
    }

    @Test
    fun `resolveTailscalePeer returns error when not found`() {
        val p1 = TailscalePeerEntry(tailscalePeer {
            stableID = "p1"
            hostName = "server"
        })
        val res = resolveTailscalePeer(listOf(p1), "nonexistent")
        assertNull(res.entry)
        assertEquals("peer not found: nonexistent", res.error)
    }

    @Test
    fun `formatTaildropSize produces expected sing-box units`() {
        assertEquals("-", formatTaildropSize(-1))
        assertEquals("0 B", formatTaildropSize(0))
        assertEquals("500 B", formatTaildropSize(500))
        assertEquals("999 B", formatTaildropSize(999))
        assertEquals("1.00 KB", formatTaildropSize(1_000))
        assertEquals("1.50 KB", formatTaildropSize(1_500))
        assertEquals("15.2 KB", formatTaildropSize(15_200))
        assertEquals("250 KB", formatTaildropSize(250_000))
        assertEquals("1.00 MB", formatTaildropSize(1_000_000))
        assertEquals("50.0 MB", formatTaildropSize(50_000_000))
        assertEquals("1.00 GB", formatTaildropSize(1_000_000_000))
    }
}
