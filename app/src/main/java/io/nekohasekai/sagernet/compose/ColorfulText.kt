package io.nekohasekai.sagernet.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.nekohasekai.sagernet.R

private val ansiRegex = Regex("\u001B\\[[;\\d]*m")

@Composable
fun String.ansiEscape(): AnnotatedString {
    val plainText = replace(ansiRegex, "")

    return buildAnnotatedString {
        append(plainText)

        val spans = mutableListOf<AnsiSpan>()
        val stack = mutableListOf<AnsiSpan>()
        val matches = ansiRegex.findAll(this@ansiEscape)
        var offset = 0

        matches.forEach { result ->
            val stringCode = result.value
            val start = result.range.last
            val end = result.range.last + 1
            val ansiInstruction = parseAnsiInstruction(stringCode)
            offset += stringCode.length

            if (ansiInstruction.decorationCode == "0" && stack.isNotEmpty()) {
                spans.add(stack.removeAt(stack.size - 1).copy(end = end - offset))
            } else {
                val span = AnsiSpan(
                    instruction = ansiInstruction,
                    start = start - if (offset > start) start else offset - 1,
                    end = 0,
                )
                stack.add(span)
            }
        }

        while (stack.isNotEmpty()) {
            spans.add(stack.removeAt(stack.size - 1).copy(end = plainText.length))
        }

        spans.forEach { ansiSpan ->
            listOfNotNull(
                getStyleForCode(ansiSpan.instruction.colorCode),
                getStyleForCode(ansiSpan.instruction.decorationCode),
            ).forEach { style ->
                addStyle(
                    style = style,
                    start = ansiSpan.start,
                    end = ansiSpan.end,
                )
            }
        }
    }
}

@Stable
private data class AnsiSpan(
    val instruction: AnsiInstruction,
    val start: Int,
    val end: Int,
)

@Stable
private data class AnsiInstruction(
    val colorCode: String?,
    val decorationCode: String?,
)

private fun parseAnsiInstruction(code: String): AnsiInstruction {
    val colorCodes = code.substringAfter('[').substringBefore('m').split(';')

    return when (colorCodes.size) {
        3 -> AnsiInstruction(
            colorCode = colorCodes[1],
            decorationCode = colorCodes[2],
        )

        2 -> AnsiInstruction(
            colorCode = colorCodes[0],
            decorationCode = colorCodes[1],
        )

        1 -> AnsiInstruction(
            colorCode = null,
            decorationCode = colorCodes[0],
        )

        else -> AnsiInstruction(null, null)
    }
}

@Composable
private fun getStyleForCode(code: String?): SpanStyle? = when (code) {
    "0", null -> null
    "1" -> SpanStyle(fontWeight = FontWeight.Bold)
    "3" -> SpanStyle(fontStyle = FontStyle.Italic)
    "4" -> SpanStyle(textDecoration = TextDecoration.Underline)
    "30" -> SpanStyle(color = Color.Gray)
    "31" -> SpanStyle(color = colorResource(R.color.log_red))
    "32" -> SpanStyle(color = colorResource(R.color.log_green))
    "33" -> SpanStyle(color = colorResource(R.color.log_yellow))
    "34" -> SpanStyle(color = colorResource(R.color.log_blue))
    "35" -> SpanStyle(color = colorResource(R.color.log_purple))
    "36" -> SpanStyle(color = colorResource(R.color.log_blue_light))
    "37" -> SpanStyle(color = colorResource(R.color.log_white))
    else -> {
        val codeInt = code.toIntOrNull()
        if (codeInt != null) {
            val normalizedCode = codeInt % 125
            val row = normalizedCode / 36
            val column = normalizedCode % 36
            SpanStyle(
                color = Color(
                    red = row * 51,
                    green = column / 6 * 51,
                    blue = column % 6 * 51,
                ),
            )
        } else {
            null
        }
    }
}

private fun Color.dim(factor: Float): Color {
    return Color(
        (red * factor).coerceIn(0f, 1f),
        (green * factor).coerceIn(0f, 1f),
        (blue * factor).coerceIn(0f, 1f),
        alpha,
    )
}

@Composable
fun colorForUrlTestDelay(urlTestDelay: Number): Color {
    val base = when (urlTestDelay) {
        in Short.MIN_VALUE..0 -> Color.Gray
        in 1..800 -> Color.Green
        in 801..1500 -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }

    return if (isSystemInDarkTheme()) {
        base.dim(0.7f)
    } else {
        base
    }
}