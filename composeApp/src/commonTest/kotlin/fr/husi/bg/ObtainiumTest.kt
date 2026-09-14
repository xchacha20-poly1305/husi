package fr.husi.bg

import fr.husi.ktx.unUrlSafe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObtainiumTest {

    private companion object {
        const val PACKAGE_NAME = "fr.husi"
    }

    private fun parseLink(link: String): Pair<JsonObject, JsonObject> {
        val prefix = "obtainium://app/"
        assertTrue(link.startsWith(prefix), "unexpected link: $link")
        val app = Json.parseToJsonElement(link.removePrefix(prefix).unUrlSafe()) as JsonObject
        val additionalSettings = Json.parseToJsonElement(
            app.getValue("additionalSettings").jsonPrimitive.content,
        ) as JsonObject
        return app to additionalSettings
    }

    @Test
    fun `link carries every field Obtainium requires to import an app`() {
        val (app, _) = parseLink(obtainiumAddAppLink(PACKAGE_NAME, false))

        assertEquals("fr.husi", app.getValue("id").jsonPrimitive.content)
        assertEquals(
            "https://github.com/xchacha20-poly1305/husi",
            app.getValue("url").jsonPrimitive.content,
        )
        assertEquals("xchacha20-poly1305", app.getValue("author").jsonPrimitive.content)
        assertEquals("husi", app.getValue("name").jsonPrimitive.content)
        assertEquals(0, app.getValue("preferredApkIndex").jsonPrimitive.int)
    }

    @Test
    fun `link always filters out plugin releases`() {
        for (includePreReleases in listOf(false, true)) {
            val (_, settings) = parseLink(obtainiumAddAppLink(PACKAGE_NAME, includePreReleases))
            val filter = Regex(settings.getValue("filterReleaseTitlesByRegEx").jsonPrimitive.content)

            assertTrue(filter.containsMatchIn("v2.1.2"))
            assertTrue(filter.containsMatchIn("v2.1.0-alpha.0"))
            assertTrue(!filter.containsMatchIn("plugin-mieru-v3.37.0-0"))
        }
    }

    @Test
    fun `pre releases are only allowed when asked for`() {
        val (_, allowed) = parseLink(obtainiumAddAppLink(PACKAGE_NAME, true))
        assertEquals(true, allowed.getValue("includePrereleases").jsonPrimitive.boolean)

        val (_, rejected) = parseLink(obtainiumAddAppLink(PACKAGE_NAME, false))
        assertEquals(false, rejected.getValue("includePrereleases").jsonPrimitive.boolean)
    }
}
