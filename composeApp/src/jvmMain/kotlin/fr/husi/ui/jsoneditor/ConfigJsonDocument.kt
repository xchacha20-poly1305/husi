package fr.husi.ui.jsoneditor

import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost

enum class ConfigJsonTokenType {
    OBJECT_KEY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    PUNCTUATION,
    INVALID;

    val isStringLiteral: Boolean get() = this == OBJECT_KEY || this == STRING
}

data class ConfigJsonToken(
    val type: ConfigJsonTokenType,
    val start: Int,
    val end: Int,
    val isTerminated: Boolean = true,
)

class ConfigJsonDocument private constructor(
    val text: String,
    val tokens: List<ConfigJsonToken>,
    private val lineStarts: IntArray,
) {

    val lineCount: Int get() = lineStarts.size

    fun tokensInLines(lines: IntRange): List<ConfigJsonToken> {
        if (tokens.isEmpty() || lines.isEmpty()) return emptyList()
        if (lines.last < 0 || lines.first > lineStarts.lastIndex) return emptyList()
        val firstLine = lines.first.fastCoerceAtLeast(0)
        val lastLine = lines.last.fastCoerceAtMost(lineStarts.lastIndex)
        val from = lineStarts[firstLine]
        val until = if (lastLine == lineStarts.lastIndex) {
            text.length
        } else {
            lineStarts[lastLine + 1]
        }
        return tokens.subList(firstTokenEndingAfter(from), firstTokenStartingAtOrAfter(until))
    }

    private fun firstTokenEndingAfter(offset: Int): Int {
        var low = 0
        var high = tokens.size
        while (low < high) {
            val middle = (low + high) / 2
            if (tokens[middle].end <= offset) low = middle + 1 else high = middle
        }
        return low
    }

    private fun firstTokenStartingAtOrAfter(offset: Int): Int {
        var low = 0
        var high = tokens.size
        while (low < high) {
            val middle = (low + high) / 2
            if (tokens[middle].start < offset) low = middle + 1 else high = middle
        }
        return low
    }

    companion object {
        fun parse(text: String): ConfigJsonDocument {
            val tokens = buildList {
                var index = 0
                while (index < text.length) {
                    val start = index
                    when (text[index]) {
                        ' ', '\t', '\r', '\n' -> index++
                        '{', '}', '[', ']', ':', ',' -> {
                            add(ConfigJsonToken(ConfigJsonTokenType.PUNCTUATION, index, index + 1))
                            index++
                        }

                        '"' -> {
                            index++
                            var escaped = false
                            var terminated = false
                            while (index < text.length) {
                                val current = text[index++]
                                if (escaped) {
                                    escaped = false
                                } else if (current == '\\') {
                                    escaped = true
                                } else if (current == '"') {
                                    terminated = true
                                    break
                                }
                            }
                            val type = if (isFollowedByColon(text, index)) {
                                ConfigJsonTokenType.OBJECT_KEY
                            } else {
                                ConfigJsonTokenType.STRING
                            }
                            add(ConfigJsonToken(type, start, index, terminated))
                        }

                        '-', in '0'..'9' -> {
                            index++
                            while (index < text.length && text[index] in numberCharacters) index++
                            add(ConfigJsonToken(ConfigJsonTokenType.NUMBER, start, index))
                        }

                        else -> {
                            index++
                            while (index < text.length && !text[index].isWhitespace() && text[index] !in delimiters) {
                                index++
                            }
                            val type = when (text.substring(start, index)) {
                                "true", "false" -> ConfigJsonTokenType.BOOLEAN
                                "null" -> ConfigJsonTokenType.NULL
                                else -> ConfigJsonTokenType.INVALID
                            }
                            add(ConfigJsonToken(type, start, index))
                        }
                    }
                }
            }
            return ConfigJsonDocument(text, tokens, lineStartsOf(text))
        }

        private fun isFollowedByColon(text: String, from: Int): Boolean {
            var index = from
            while (index < text.length && text[index].isWhitespace()) index++
            return index < text.length && text[index] == ':'
        }

        private fun lineStartsOf(text: String): IntArray {
            val starts = mutableListOf(0)
            for (index in text.indices) {
                if (text[index] == '\n') starts += index + 1
            }
            return starts.toIntArray()
        }
    }
}

class ConfigJsonEngine {
    private var cachedDocument = ConfigJsonDocument.parse("")

    fun document(text: String): ConfigJsonDocument {
        if (cachedDocument.text != text) cachedDocument = ConfigJsonDocument.parse(text)
        return cachedDocument
    }
}

private val numberCharacters = setOf(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', 'e', 'E', '+', '-',
)
private val delimiters = setOf('{', '}', '[', ']', ':', ',', '"')

val configJsonEngine = ConfigJsonEngine()
