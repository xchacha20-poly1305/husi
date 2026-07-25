package fr.husi.ui.jsoneditor

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.ui.text.TextRange

private const val configIndent = "  "

internal data class SmartNewline(
    val text: String,
    val cursorOffset: Int,
)

internal fun smartNewline(text: CharSequence, selection: TextRange): SmartNewline {
    val start = selection.min
    val end = selection.max
    val lineStart = text.lastIndexOf('\n', startIndex = start - 1).let {
        if (it == -1) 0 else it + 1
    }
    val leadingWhitespace = text.substring(lineStart, start).takeWhile {
        it == ' ' || it == '\t'
    }
    val previousIndex = (start - 1 downTo lineStart).firstOrNull { !text[it].isWhitespace() }
    val nextIndex = (end until text.length).firstOrNull { !text[it].isWhitespace() }
    val opening = previousIndex?.let(text::get)
    val closing = nextIndex?.let(text::get)
    val opensBlock = (opening == '{' || opening == '[') &&
        !isInsideJsonString(text, previousIndex)
    val closesBlock = (opening == '{' && closing == '}') ||
        (opening == '[' && closing == ']')

    if (opensBlock && closesBlock) {
        val innerIndent = leadingWhitespace + configIndent
        return SmartNewline(
            text = "\n$innerIndent\n$leadingWhitespace",
            cursorOffset = 1 + innerIndent.length,
        )
    }

    val nextIndent = if (opensBlock) leadingWhitespace + configIndent else leadingWhitespace
    return SmartNewline(
        text = "\n$nextIndent",
        cursorOffset = 1 + nextIndent.length,
    )
}

private fun isInsideJsonString(text: CharSequence, position: Int): Boolean {
    var insideString = false
    var escaped = false
    for (index in 0..position) {
        val character = text[index]
        if (escaped) {
            escaped = false
        } else if (character == '\\' && insideString) {
            escaped = true
        } else if (character == '"') {
            insideString = !insideString
        }
    }
    return insideString
}

internal val configEditInputTransformation = InputTransformation {
    val originalSelection = originalSelection
    val start = originalSelection.min
    val end = originalSelection.max
    val expectedText = originalText.substring(0, start) + "\n" + originalText.substring(end)
    if (asCharSequence().toString() != expectedText) return@InputTransformation

    val newline = smartNewline(originalText, originalSelection)
    replace(start, start + 1, newline.text)
    selection = TextRange(start + newline.cursorOffset)
}
