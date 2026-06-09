package fr.husi.ui

import fr.husi.fmt.toUniversalLink
import fr.husi.fmt.v2ray.VLESSBean
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportLinkInteractorTest {

    @Test
    fun `parseUri should import husi profile links as profiles`() = runTest {
        val link = VLESSBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            uuid = "00000000-0000-0000-0000-000000000000"
            name = "node"
        }.toUniversalLink()

        val preview = ImportLinkInteractor().parseUri(link)
        val profiles = assertIs<ImportLinkPreview.Profiles>(preview).proxies
        val bean = assertIs<VLESSBean>(profiles.single())

        assertEquals("example.com", bean.serverAddress)
        assertEquals("node", bean.name)
    }

    @Test
    fun `parseSubscription should ignore husi profile links`() {
        val link = VLESSBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            uuid = "00000000-0000-0000-0000-000000000000"
        }.toUniversalLink()

        assertNull(ImportLinkInteractor().parseSubscription(link))
    }

    @Test
    fun `isSubscriptionUri should only match explicit subscription schemes`() {
        assertTrue(isSubscriptionUri("husi://subscription?abc"))
        assertTrue(isSubscriptionUri("sing-box://import-remote-profile?url=https%3A%2F%2Fexample.com"))
        assertFalse(isSubscriptionUri("husi://vless?abc"))
        assertFalse(isSubscriptionUri("sing-box://outbound?abc"))
    }
}
