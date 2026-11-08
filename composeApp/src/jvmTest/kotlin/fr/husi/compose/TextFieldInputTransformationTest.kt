package fr.husi.compose

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class TextFieldInputTransformationTest {
    @Test
    fun `deleting the last digit keeps a single zero`() {
        val state = editNumber("7", "")
        assertEquals("0", state.text.toString())
        assertEquals(TextRange(1), state.selection)
    }

    @Test
    fun `zero input never becomes empty`() {
        assertEquals("0", editNumber("1", "000").text.toString())
    }

    @Test
    fun `removing leading zeros preserves the cursor relative to remaining digits`() {
        val state = editNumber("1", "00123", cursor = 3)
        assertEquals("123", state.text.toString())
        assertEquals(TextRange(1), state.selection)
    }

    @Test
    fun `invalid and overflowing unsigned numbers restore the previous edit`() {
        for (input in listOf("-1", "12a", "4294967296")) {
            val state = editNumber("42", input)
            assertEquals("42", state.text.toString(), input)
            assertEquals(TextRange(2), state.selection, input)
        }
        assertEquals("4294967295", editNumber("42", "4294967295").text.toString())
    }

    @Test
    fun `port filter accepts boundaries and rejects out of range input`() {
        for (input in listOf("0", "65535")) {
            assertEquals(input, editNumber("80", input, maximum = 65535u).text.toString())
        }
        for (input in listOf("65536", "-1")) {
            assertEquals("80", editNumber("80", input, maximum = 65535u).text.toString())
        }
    }

    private fun editNumber(
        initial: String,
        input: String,
        cursor: Int = input.length,
        maximum: UInt = UInt.MAX_VALUE,
    ): TextFieldState {
        val state = TextFieldState(initial, TextRange(initial.length))
        state.edit {
            replace(0, length, input)
            selection = TextRange(cursor)
            with(unsignedIntegerInputTransformation(maximum)) { transformInput() }
        }
        return state
    }
}
