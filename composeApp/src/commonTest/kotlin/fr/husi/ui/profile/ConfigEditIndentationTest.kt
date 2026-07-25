package fr.husi.ui.profile

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigEditIndentationTest {

    @Test
    fun `newline between braces creates an indented blank line`() {
        assertNewline(
            before = "{}",
            cursor = 1,
            expectedText = "{\n  \n}",
            expectedCursor = 4,
        )
    }

    @Test
    fun `newline after opening brace increases current indentation`() {
        assertNewline(
            before = "{\n  \"log\": {",
            cursor = 12,
            expectedText = "{\n  \"log\": {\n    ",
            expectedCursor = 17,
        )
    }

    @Test
    fun `newline after a value preserves current indentation`() {
        val before = "{\n  \"log\": {}"
        assertNewline(
            before = before,
            cursor = before.length,
            expectedText = "$before\n  ",
            expectedCursor = before.length + 3,
        )
    }

    @Test
    fun `brace inside a string does not increase indentation`() {
        val before = "{\n  \"value\": \"{"
        assertNewline(
            before = before,
            cursor = before.length,
            expectedText = "$before\n  ",
            expectedCursor = before.length + 3,
        )
    }

    @Test
    fun `newline replaces selection and uses its line indentation`() {
        val before = "{\n  true"
        val newline = smartNewline(before, TextRange(4, before.length))

        assertEquals("\n  ", newline.text)
        assertEquals(3, newline.cursorOffset)
    }

    private fun assertNewline(
        before: String,
        cursor: Int,
        expectedText: String,
        expectedCursor: Int,
    ) {
        val newline = smartNewline(before, TextRange(cursor))
        val actualText = before.substring(0, cursor) + newline.text + before.substring(cursor)

        assertEquals(expectedText, actualText)
        assertEquals(expectedCursor, cursor + newline.cursorOffset)
    }
}
