package fr.husi.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class MacOsSystemProxyBackendTest {

    @Test
    fun `parseNetworkServices skips the legend, blank lines and disabled services`() {
        val output = "" +
            "An asterisk (*) denotes that a network service is disabled.\n" +
            "USB 10/100/1000 LAN\n" +
            "Wi-Fi\n" +
            "Thunderbolt Bridge\n" +
            "*Bluetooth PAN\n" +
            "\n"

        assertEquals(
            listOf("USB 10/100/1000 LAN", "Wi-Fi", "Thunderbolt Bridge"),
            parseNetworkServices(output),
        )
    }

    @Test
    fun `parseNetworkServices handles CRLF and disabled services without a space`() {
        val output = "" +
            "An asterisk (*) denotes that a network service is disabled.\r\n" +
            "*iPhone USB\r\n" +
            "Ethernet\r\n"

        assertEquals(listOf("Ethernet"), parseNetworkServices(output))
    }

    @Test
    fun `enable sets every proxy kind on each enabled service`() {
        val commands = mutableListOf<List<String>>()
        val backend = MacOsSystemProxyBackend { command ->
            commands += command
            if (command.last() == "-listallnetworkservices") {
                "An asterisk (*) denotes that a network service is disabled.\nWi-Fi\n*Ethernet\n"
            } else {
                ""
            }
        }

        backend.enable("127.0.0.1", 2080)

        assertEquals(
            listOf(
                listOf("networksetup", "-listallnetworkservices"),
                listOf("networksetup", "-setwebproxy", "Wi-Fi", "127.0.0.1", "2080"),
                listOf("networksetup", "-setsecurewebproxy", "Wi-Fi", "127.0.0.1", "2080"),
                listOf("networksetup", "-setsocksfirewallproxy", "Wi-Fi", "127.0.0.1", "2080"),
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
}
