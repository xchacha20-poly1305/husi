package fr.husi.repository

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacOsSystemProxyBackendTest {

    @Test
    fun `parseNetworkServiceOrder skips legend, blank lines, and disabled services`() {
        val output = "" +
            "An asterisk (*) denotes that a network service is disabled.\r\n" +
            "(1) USB 10/100/1000 LAN\r\n" +
            "(Hardware Port: USB 10/100/1000 LAN, Device: en5)\r\n" +
            "\r\n" +
            "(2) Wi-Fi\n" +
            "(Hardware Port: Wi-Fi, Device: en0)\n" +
            "\n" +
            "(*) Bluetooth PAN\n" +
            "(Hardware Port: Bluetooth PAN, Device: en6)\n" +
            "( * ) iPhone USB\r\n" +
            "(Hardware Port: iPhone USB, Device: en7)\r\n"

        assertEquals(
            listOf(
                MacOsNetworkService(name = "USB 10/100/1000 LAN", device = "en5"),
                MacOsNetworkService(name = "Wi-Fi", device = "en0"),
            ),
            parseNetworkServiceOrder(output),
        )
    }

    @Test
    fun `parseNetworkServiceOrder handles parentheses, blank devices, and missing detail lines`() {
        val output = "" +
            "An asterisk (*) denotes that a network service is disabled.\n" +
            "(1) Corporate VPN\n" +
            "(2) USB LAN (2)\n" +
            "(Hardware Port: USB 10/100/1000 LAN (2), Device: en5)\n" +
            "(3) iPhone USB\n" +
            "(Hardware Port: iPhone USB, Device: )\n" +
            "(4) IPSec Tunnel\n" +
            "(Hardware Port: IPSec)\n" +
            "(5) Tail-End Service\n"

        assertEquals(
            listOf(
                MacOsNetworkService(name = "Corporate VPN", device = null),
                MacOsNetworkService(name = "USB LAN (2)", device = "en5"),
                MacOsNetworkService(name = "iPhone USB", device = null),
                MacOsNetworkService(name = "IPSec Tunnel", device = null),
                MacOsNetworkService(name = "Tail-End Service", device = null),
            ),
            parseNetworkServiceOrder(output),
        )
    }

    @Test
    fun `selectTargetNetworkServices restricts to active devices when matched`() {
        val services = listOf(
            MacOsNetworkService(name = "USB LAN", device = "en5"),
            MacOsNetworkService(name = "Wi-Fi", device = "en0"),
            MacOsNetworkService(name = "Thunderbolt Bridge", device = "bridge0"),
            MacOsNetworkService(name = "Corporate VPN", device = null),
        )

        assertEquals(
            listOf("Wi-Fi"),
            selectTargetNetworkServices(services, setOf("en0", "utun3")),
        )
    }

    @Test
    fun `selectTargetNetworkServices falls back to all enabled services when activeDevices is empty or unmatched`() {
        val services = listOf(
            MacOsNetworkService(name = "USB LAN", device = "en5"),
            MacOsNetworkService(name = "Wi-Fi", device = "en0"),
            MacOsNetworkService(name = "Corporate VPN", device = null),
        )

        assertEquals(
            listOf("USB LAN", "Wi-Fi", "Corporate VPN"),
            selectTargetNetworkServices(services, emptySet()),
        )
        assertEquals(
            listOf("USB LAN", "Wi-Fi", "Corporate VPN"),
            selectTargetNetworkServices(services, setOf("utun3")),
        )
    }

    @Test
    fun `hasRoutableAddress rejects non-routable addresses and accepts routable IPv4 and IPv6`() {
        val nonRoutable = listOf("127.0.0.1", "169.254.1.2", "fe80::1", "::", "224.0.0.1")
            .map(InetAddress::getByName)
        for (address in nonRoutable) {
            assertFalse(hasRoutableAddress(listOf(address)), "Expected $address to be rejected")
        }
        assertFalse(hasRoutableAddress(nonRoutable))

        assertTrue(hasRoutableAddress(listOf(InetAddress.getByName("192.168.1.10"))))
        assertTrue(hasRoutableAddress(listOf(InetAddress.getByName("2001:db8::1"))))
    }

    @Test
    fun `enable configures only active services`() {
        val commands = mutableListOf<List<String>>()
        val backend = MacOsSystemProxyBackend(
            runner = { command ->
                commands += command
                if (command.last() == "-listnetworkserviceorder") SAMPLE_SERVICE_ORDER else ""
            },
            activeDevicesProvider = { setOf("en0") },
        )

        backend.enable("127.0.0.1", 2080)

        assertEquals(
            listOf(
                listOf("networksetup", "-listnetworkserviceorder"),
                listOf("networksetup", "-setwebproxy", "Wi-Fi", "127.0.0.1", "2080"),
                listOf("networksetup", "-setsecurewebproxy", "Wi-Fi", "127.0.0.1", "2080"),
                listOf("networksetup", "-setsocksfirewallproxy", "Wi-Fi", "127.0.0.1", "2080"),
            ),
            commands,
        )
    }

    @Test
    fun `disable after enable disables tracked services without querying network service order`() {
        val commands = mutableListOf<List<String>>()
        val backend = MacOsSystemProxyBackend(
            runner = { command ->
                commands += command
                if (command.last() == "-listnetworkserviceorder") SAMPLE_SERVICE_ORDER else ""
            },
            activeDevicesProvider = { setOf("en0") },
        )

        backend.enable("127.0.0.1", 2080)
        commands.clear()

        backend.disable()

        assertEquals(
            listOf(
                listOf("networksetup", "-setwebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsecurewebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsocksfirewallproxystate", "Wi-Fi", "off"),
            ),
            commands,
        )
    }

    @Test
    fun `disable before enable queries network service order and disables target services`() {
        val commands = mutableListOf<List<String>>()
        val backend = MacOsSystemProxyBackend(
            runner = { command ->
                commands += command
                if (command.last() == "-listnetworkserviceorder") SAMPLE_SERVICE_ORDER else ""
            },
            activeDevicesProvider = { setOf("en0") },
        )

        backend.disable()

        assertEquals(
            listOf(
                listOf("networksetup", "-listnetworkserviceorder"),
                listOf("networksetup", "-setwebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsecurewebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsocksfirewallproxystate", "Wi-Fi", "off"),
            ),
            commands,
        )
    }

    @Test
    fun `enable after switching active interfaces disables stale service and enables new active service`() {
        val commands = mutableListOf<List<String>>()
        var activeDevices = setOf("en0")
        val backend = MacOsSystemProxyBackend(
            runner = { command ->
                commands += command
                if (command.last() == "-listnetworkserviceorder") SAMPLE_SERVICE_ORDER else ""
            },
            activeDevicesProvider = { activeDevices },
        )

        backend.enable("127.0.0.1", 2080)
        commands.clear()
        activeDevices = setOf("en5")

        backend.enable("127.0.0.1", 2080)

        assertEquals(
            listOf(
                listOf("networksetup", "-listnetworkserviceorder"),
                listOf("networksetup", "-setwebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsecurewebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsocksfirewallproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setwebproxy", "USB LAN", "127.0.0.1", "2080"),
                listOf("networksetup", "-setsecurewebproxy", "USB LAN", "127.0.0.1", "2080"),
                listOf("networksetup", "-setsocksfirewallproxy", "USB LAN", "127.0.0.1", "2080"),
            ),
            commands,
        )

        commands.clear()
        backend.disable()

        assertEquals(
            listOf(
                listOf("networksetup", "-setwebproxystate", "USB LAN", "off"),
                listOf("networksetup", "-setsecurewebproxystate", "USB LAN", "off"),
                listOf("networksetup", "-setsocksfirewallproxystate", "USB LAN", "off"),
            ),
            commands,
        )
    }

    @Test
    fun `enable ignores stale disable failures and keeps enabling active services`() {
        val commands = mutableListOf<List<String>>()
        var activeDevices = setOf("en0")
        val backend = MacOsSystemProxyBackend(
            runner = { command ->
                commands += command
                when {
                    command.last() == "-listnetworkserviceorder" -> SAMPLE_SERVICE_ORDER
                    command[1] == "-setwebproxystate" && command[2] == "Wi-Fi" -> {
                        error("stale interface unavailable")
                    }
                    else -> ""
                }
            },
            activeDevicesProvider = { activeDevices },
        )

        backend.enable("127.0.0.1", 2080)
        commands.clear()
        activeDevices = setOf("en5")

        backend.enable("127.0.0.1", 2080)

        assertEquals(
            listOf(
                listOf("networksetup", "-listnetworkserviceorder"),
                listOf("networksetup", "-setwebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsecurewebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsocksfirewallproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setwebproxy", "USB LAN", "127.0.0.1", "2080"),
                listOf("networksetup", "-setsecurewebproxy", "USB LAN", "127.0.0.1", "2080"),
                listOf("networksetup", "-setsocksfirewallproxy", "USB LAN", "127.0.0.1", "2080"),
            ),
            commands,
        )
    }

    @Test
    fun `enable tracks partial service update so disable cleans it up`() {
        val commands = mutableListOf<List<String>>()
        var failSecureProxy = true
        val backend = MacOsSystemProxyBackend(
            runner = { command ->
                commands += command
                when {
                    command.last() == "-listnetworkserviceorder" -> SAMPLE_SERVICE_ORDER
                    failSecureProxy && command[1] == "-setsecurewebproxy" -> error("secure proxy failed")
                    else -> ""
                }
            },
            activeDevicesProvider = { setOf("en0") },
        )

        assertFailsWith<IllegalStateException> {
            backend.enable("127.0.0.1", 2080)
        }

        commands.clear()
        failSecureProxy = false
        backend.disable()

        assertEquals(
            listOf(
                listOf("networksetup", "-setwebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsecurewebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsocksfirewallproxystate", "Wi-Fi", "off"),
            ),
            commands,
        )
    }

    @Test
    fun `disable continues disabling remaining proxy kinds and services on error and rethrows first error`() {
        val commands = mutableListOf<List<String>>()
        val backend = MacOsSystemProxyBackend(
            runner = { command ->
                commands += command
                when {
                    command.last() == "-listnetworkserviceorder" -> SAMPLE_SERVICE_ORDER
                    command[1] == "-setwebproxystate" && command[2] == "USB LAN" -> {
                        error("first error")
                    }
                    command[1] == "-setsecurewebproxystate" && command[2] == "Wi-Fi" -> {
                        error("second error")
                    }
                    else -> ""
                }
            },
            activeDevicesProvider = { setOf("en5", "en0") },
        )

        val thrown = assertFailsWith<IllegalStateException> {
            backend.disable()
        }
        assertEquals("first error", thrown.message)

        assertEquals(
            listOf(
                listOf("networksetup", "-listnetworkserviceorder"),
                listOf("networksetup", "-setwebproxystate", "USB LAN", "off"),
                listOf("networksetup", "-setsecurewebproxystate", "USB LAN", "off"),
                listOf("networksetup", "-setsocksfirewallproxystate", "USB LAN", "off"),
                listOf("networksetup", "-setwebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsecurewebproxystate", "Wi-Fi", "off"),
                listOf("networksetup", "-setsocksfirewallproxystate", "Wi-Fi", "off"),
            ),
            commands,
        )
    }

    @Test
    fun `networksetupDisableArgs turns every proxy kind off`() {
        assertEquals(
            listOf(
                listOf("-setwebproxystate", "Wi-Fi", "off"),
                listOf("-setsecurewebproxystate", "Wi-Fi", "off"),
                listOf("-setsocksfirewallproxystate", "Wi-Fi", "off"),
            ),
            networksetupDisableArgs("Wi-Fi"),
        )
    }

    private companion object {
        const val SAMPLE_SERVICE_ORDER = "" +
            "An asterisk (*) denotes that a network service is disabled.\n" +
            "(1) USB LAN\n" +
            "(Hardware Port: USB 10/100/1000 LAN, Device: en5)\n" +
            "(2) Wi-Fi\n" +
            "(Hardware Port: Wi-Fi, Device: en0)\n" +
            "(3) Thunderbolt Bridge\n" +
            "(Hardware Port: Thunderbolt Bridge, Device: bridge0)\n" +
            "(*) Bluetooth PAN\n" +
            "(Hardware Port: Bluetooth PAN, Device: en6)\n"
    }
}
