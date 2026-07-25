package fr.husi.ui.jsoneditor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ConfigJsonDocumentTest {

    @Test
    fun `lexer recognizes every JSON token type`() {
        val text = """{"key":"value","number":-1.5e+2,"enabled":true,"missing":null}"""

        val tokens = ConfigJsonDocument.parse(text).tokens

        assertEquals(
            setOf(
                ConfigJsonTokenType.STRING,
                ConfigJsonTokenType.NUMBER,
                ConfigJsonTokenType.BOOLEAN,
                ConfigJsonTokenType.NULL,
                ConfigJsonTokenType.PUNCTUATION,
            ),
            tokens.mapTo(mutableSetOf()) { it.type },
        )
    }

    @Test
    fun `lexer keeps escaped quote inside a string`() {
        val text = """{"key":"escaped \" quote"}"""

        val strings = ConfigJsonDocument.parse(text).tokens.filter { it.type == ConfigJsonTokenType.STRING }

        assertEquals(listOf("\"key\"", "\"escaped \\\" quote\""), strings.map { text.substring(it.start, it.end) })
    }

    @Test
    fun `lexer highlights an unfinished string while editing`() {
        val text = """{"key":"unfinished"""

        val token = ConfigJsonDocument.parse(text).tokens.last()

        assertEquals(ConfigJsonTokenType.STRING, token.type)
        assertEquals(text.length, token.end)
    }

    @Test
    fun `unknown bare value is marked invalid`() {
        val document = ConfigJsonDocument.parse("""{"key":unknown}""")

        assertEquals(ConfigJsonTokenType.INVALID, document.tokens.first { it.type == ConfigJsonTokenType.INVALID }.type)
    }

    @Test
    fun `engine reuses the document for highlighting and completion`() {
        val engine = ConfigJsonEngine()

        assertSame(engine.document("{}"), engine.document("{}"))
    }
}
