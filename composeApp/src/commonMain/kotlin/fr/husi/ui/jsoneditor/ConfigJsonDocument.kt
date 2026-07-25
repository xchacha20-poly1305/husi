package fr.husi.ui.jsoneditor

enum class ConfigJsonTokenType {
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    PUNCTUATION,
    INVALID,
}

data class ConfigJsonToken(
    val type: ConfigJsonTokenType,
    val start: Int,
    val end: Int,
)

class ConfigJsonDocument private constructor(
    val text: String,
    val tokens: List<ConfigJsonToken>,
) {

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
                            while (index < text.length) {
                                val current = text[index++]
                                if (escaped) {
                                    escaped = false
                                } else if (current == '\\') {
                                    escaped = true
                                } else if (current == '"') {
                                    break
                                }
                            }
                            add(ConfigJsonToken(ConfigJsonTokenType.STRING, start, index))
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
            return ConfigJsonDocument(text, tokens)
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
