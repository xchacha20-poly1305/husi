package fr.husi.bg

import fr.husi.ktx.kxs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GithubReleaseTest {

    @Test
    fun `explicit null body decodes to the default`() {
        val json = """
            {
              "tag_name": "202609100029",
              "body": null,
              "html_url": "https://example.invalid/releases/tag/202609100029"
            }
        """.trimIndent()

        val release = kxs.decodeFromString<GithubRelease>(json)

        assertEquals("202609100029", release.tagName)
        assertEquals("", release.body)
        assertTrue(release.assets.isEmpty())
    }

    @Test
    fun `explicit null asset fields decode to their defaults`() {
        val json = """
            {
              "tag_name": "v1",
              "assets": [
                {
                  "name": "geoip.db",
                  "browser_download_url": null,
                  "size": null,
                  "digest": null
                }
              ]
            }
        """.trimIndent()

        val asset = kxs.decodeFromString<GithubRelease>(json).assets.single()

        assertEquals("geoip.db", asset.name)
        assertEquals("", asset.browserDownloadUrl)
        assertEquals(0L, asset.size)
        assertNull(asset.sha256Hex())
    }
}
