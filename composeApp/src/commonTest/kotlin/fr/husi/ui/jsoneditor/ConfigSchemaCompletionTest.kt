package fr.husi.ui.jsoneditor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ConfigSchemaCompletionTest {

    private val completer = ConfigSchemaCompleter(
        Json.parseToJsonElement(
            $$"""
            {
              "$ref": "#/$defs/Options",
              "$defs": {
                "Options": {
                  "type": "object",
                  "properties": {
                    "log": {"$ref": "#/$defs/Log"},
                    "outbounds": {"type": "array", "items": {"$ref": "#/$defs/Outbound"}},
                    "route": {"type": "object"}
                  }
                },
                "Log": {
                  "allOf": [
                    {"type": "object", "properties": {"disabled": {"type": "boolean"}}},
                    {"type": "object", "properties": {"level": {"enum": ["debug", "info"]}}}
                  ]
                },
                "Outbound": {
                  "oneOf": [
                    {
                      "allOf": [
                        {"$ref": "#/$defs/OutboundBase"},
                        {"properties": {"type": {"const": "direct"}}}
                      ]
                    },
                    {
                      "allOf": [
                        {"$ref": "#/$defs/OutboundBase"},
                        {"properties": {"type": {"const": "shadowsocks"}, "server": {"type": "string"}}}
                      ]
                    }
                  ]
                },
                "OutboundBase": {"type": "object", "properties": {"tag": {"type": "string"}}}
              }
            }
            """.trimIndent(),
        ).jsonObject,
    )

    @Test
    fun `empty object does not automatically offer schema properties`() {
        assertEquals(emptyList(), completer.complete("{", 1))
    }

    @Test
    fun `opening a key string follows root ref`() {
        val completions = completer.complete("{\"", 2)

        assertContains(completions.map { it.label }, "log")
        assertContains(completions.map { it.label }, "outbounds")
        assertContains(completions.map { it.label }, "route")
    }

    @Test
    fun `allOf exposes matching nested property`() {
        val text = "{\"log\": {\"l"

        val completion = completer.complete(text, text.length).single()

        assertEquals("level", completion.label)
    }

    @Test
    fun `const values from oneOf branches are offered`() {
        val text = "{\"outbounds\": [{\"type\": \"d"

        val completions = completer.complete(text, text.length)

        assertContains(completions.map { it.label }, "direct")
    }

    @Test
    fun `oneOf and allOf expose properties from every branch`() {
        val text = "{\"outbounds\": [{\"s"

        val completions = completer.complete(text, text.length)

        assertContains(completions.map { it.label }, "server")
    }

    @Test
    fun `const completion replaces an existing closing quote`() {
        val text = "{\"outbounds\": [{\"type\": \"d\"}] }"
        val cursor = text.indexOf("\"d\"") + 2

        val completion = completer.complete(text, cursor).first { it.label == "direct" }

        assertEquals(cursor + 1, completion.replaceEnd)
    }

    @Test
    fun `string value does not offer an empty string while editing`() {
        val text = "{\"outbounds\": [{\"tag\": \"tag"

        assertEquals(emptyList(), completer.complete(text, text.length))
    }

    @Test
    fun `cursor after a completed string is no longer treated as inside it`() {
        val text = "{\"log\"}"
        val cursor = text.indexOf('}')

        assertEquals(emptyList(), completer.complete(text, cursor))
    }

    @Test
    fun `libcore schema offers real root and outbound completions`() {
        val rootCompletions = ConfigSchema.CONFIG.completer.complete("{\"", 2)
        val outboundText = "{\"outbounds\": [{\"type\": \"d"
        val outboundCompletions = ConfigSchema.CONFIG.completer.complete(outboundText, outboundText.length)

        assertContains(rootCompletions.map { it.label }, "log")
        assertContains(rootCompletions.map { it.label }, "outbounds")
        assertContains(outboundCompletions.map { it.label }, "direct")
    }

    @Test
    fun `libcore outbound schema offers outbound properties at root`() {
        val text = "{\"type\": \"d"
        val completions = ConfigSchema.OUTBOUND.completer.complete(text, text.length)

        assertContains(completions.map { it.label }, "direct")
    }

    @Test
    fun `libcore DNS rule schema offers DNS rule properties at root`() {
        val completions = ConfigSchema.DNS_RULE.completer.complete("{\"", 2)

        assertContains(completions.map { it.label }, "domain")
    }
}
