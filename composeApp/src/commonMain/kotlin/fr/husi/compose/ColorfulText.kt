package fr.husi.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.util.fastCoerceIn
import fr.husi.compose.theme.LogColors
import fr.husi.compose.theme.LocalAppDarkMode

private val ansiRegex = Regex("\u001B\\[[;\\d]*m")

@Composable
fun String.ansiEscape(highlightQuery: String? = null): AnnotatedString {
    val ansiParsed = remember(this) { parseAnsi() }

    if (highlightQuery.isNullOrEmpty()) return ansiParsed

    val highlightColor = MaterialTheme.colorScheme.onTertiaryContainer
    val highlightBg = MaterialTheme.colorScheme.tertiaryContainer

    return remember(ansiParsed, highlightQuery, highlightColor, highlightBg) {
        buildAnnotatedString {
            append(ansiParsed)
            val highlightStyle = SpanStyle(color = highlightColor, background = highlightBg)
            val lowerText = ansiParsed.text.lowercase()
            var searchStart = 0
            while (true) {
                val index = lowerText.indexOf(highlightQuery, searchStart)
                if (index < 0) break
                addStyle(highlightStyle, index, index + highlightQuery.length)
                searchStart = index + highlightQuery.length
            }
        }
    }
}

private fun String.parseAnsi(): AnnotatedString {
    val plainText = replace(ansiRegex, "")
    val matches = ansiRegex.findAll(this).toList()

    if (matches.isEmpty()) {
        return AnnotatedString(plainText)
    }

    return buildAnnotatedString {
        append(plainText)

        var currentStyle: SpanStyle? = null
        var currentStart = 0
        var offset = 0

        matches.forEach { match ->
            val code = match.value
            val codeStart = match.range.first - offset
            val style = parseAnsiCode(code)

            if (style == null) {
                if (currentStyle != null && currentStart < codeStart) {
                    addStyle(currentStyle, currentStart, codeStart)
                }
                currentStyle = null
                currentStart = codeStart
            } else {
                if (currentStyle != null && currentStart < codeStart) {
                    addStyle(currentStyle, currentStart, codeStart)
                }
                currentStyle = style
                currentStart = codeStart
            }

            offset += code.length
        }

        if (currentStyle != null && currentStart < plainText.length) {
            addStyle(currentStyle, currentStart, plainText.length)
        }
    }
}

private fun parseAnsiCode(code: String): SpanStyle? {
    val codes = code.substringAfter('[').substringBefore('m').split(';')

    var color: Color? = null
    var fontWeight: FontWeight? = null
    var fontStyle: FontStyle? = null
    var textDecoration: TextDecoration? = null

    codes.forEach { codeStr ->
        when (codeStr) {
            "0" -> return null // Reset
            "1" -> fontWeight = FontWeight.Bold
            "3" -> fontStyle = FontStyle.Italic
            "4" -> textDecoration = TextDecoration.Underline
            "30" -> color = Color.Gray
            "31" -> color = LogColors.red
            "32" -> color = LogColors.green
            "33" -> color = LogColors.yellow
            "34" -> color = LogColors.blue
            "35" -> color = LogColors.purple
            "36" -> color = LogColors.blueLight
            "37" -> color = LogColors.white
            else -> {
                val codeInt = codeStr.toIntOrNull()
                if (codeInt != null && codeInt in 38..125) {
                    val adjustedCode = codeInt % 125
                    val row = adjustedCode / 36
                    val column = adjustedCode % 36
                    color = Color(
                        red = row * 51,
                        green = (column / 6) * 51,
                        blue = (column % 6) * 51,
                    )
                }
            }
        }
    }

    return if (color != null || fontWeight != null || fontStyle != null || textDecoration != null) {
        SpanStyle(
            color = color ?: Color.Unspecified,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration,
        )
    } else {
        null
    }
}

private fun Color.dim(factor: Float): Color {
    return Color(
        (red * factor).fastCoerceIn(0f, 1f),
        (green * factor).fastCoerceIn(0f, 1f),
        (blue * factor).fastCoerceIn(0f, 1f),
        alpha,
    )
}

@Composable
fun colorForUrlTestDelay(urlTestDelay: Int): Color {
    val isDarkMode = LocalAppDarkMode.current
    val base = when (urlTestDelay) {
        in Short.MIN_VALUE..0 -> Color.Gray
        in 1..800 -> Color.Green
        in 801..1500 -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }

    return if (isDarkMode) {
        base.dim(0.7f)
    } else {
        base
    }
}
