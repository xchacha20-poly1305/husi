package fr.husi.compose

import kotlin.test.Test
import kotlin.test.assertEquals

class TunInterfaceNameValidationTest {

    @Test
    fun `validateMacOsTunInterfaceName accepts utun with digits`() {
        assertEquals(null, validateMacOsTunInterfaceName("utun7"))
    }

    @Test
    fun `validateMacOsTunInterfaceName rejects other names`() {
        assertEquals("Must match utun<number>", validateMacOsTunInterfaceName("tun0"))
    }

    @Test
    fun `validateLinuxTunInterfaceName enforces platform rules`() {
        assertEquals(null, validateLinuxTunInterfaceName("tun-test"))
        assertEquals(
            "Characters '/', space, and '@' are not allowed",
            validateLinuxTunInterfaceName("tun/test"),
        )
        assertEquals(
            "Only printable ASCII is allowed",
            validateLinuxTunInterfaceName("tun名字"),
        )
        assertEquals(
            "Must be 15 characters or fewer",
            validateLinuxTunInterfaceName("abcdefghijklmnop"),
        )
    }

    @Test
    fun `validateWindowsTunInterfaceName enforces platform rules`() {
        assertEquals(null, validateWindowsTunInterfaceName("tun-name"))
        assertEquals(
            "Characters \\ / : * ? \" < > | are not allowed",
            validateWindowsTunInterfaceName("tun|name"),
        )
        assertEquals(
            "Must be 255 characters or fewer",
            validateWindowsTunInterfaceName("a".repeat(256)),
        )
    }
}