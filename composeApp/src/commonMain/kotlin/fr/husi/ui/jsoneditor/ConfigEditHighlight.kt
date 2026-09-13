package fr.husi.ui.jsoneditor

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.util.fastCoerceAtLeast

internal const val highlightChunkLines = 64

internal class ConfigJsonSyntaxStyles(colorScheme: ColorScheme) {

    private companion object {
        val objectKeyColor = Color(0xFFFD971F)
        val stringColor = Color(0xFFE6DB74)
        val numberColor = Color(0xFF66D9EE)
        val booleanColor = Color(0xFFFA2772)
        val nullColor = Color(0xFFA7E22E)


        const val QUOTE_ALPHA = 0.55f
        const val PUNCTUATION_ALPHA = 0.7f
    }

    private val contentStyles = mapOf(
        ConfigJsonTokenType.OBJECT_KEY to SpanStyle(color = objectKeyColor),
        ConfigJsonTokenType.STRING to SpanStyle(color = stringColor),
        ConfigJsonTokenType.NUMBER to SpanStyle(color = numberColor),
        ConfigJsonTokenType.BOOLEAN to SpanStyle(color = booleanColor),
        ConfigJsonTokenType.NULL to SpanStyle(color = nullColor),
        ConfigJsonTokenType.PUNCTUATION to SpanStyle(
            color = colorScheme.onSurface.copy(alpha = PUNCTUATION_ALPHA),
        ),
        ConfigJsonTokenType.INVALID to SpanStyle(color = colorScheme.error),
    )

    private val quoteStyles = contentStyles
        .filterKeys { type -> type.isStringLiteral }
        .mapValues { (_, style) -> SpanStyle(color = style.color.copy(alpha = QUOTE_ALPHA)) }

    fun contentStyle(type: ConfigJsonTokenType): SpanStyle = contentStyles.getValue(type)

    fun quoteStyle(type: ConfigJsonTokenType): SpanStyle = quoteStyles.getValue(type)
}

internal fun configJsonOutputTransformation(
    styles: ConfigJsonSyntaxStyles,
    highlightedLines: () -> IntRange,
) = OutputTransformation {
    val document = configJsonEngine.document(asCharSequence().toString())
    for (token in document.tokensInLines(highlightedLines())) {
        if (!token.type.isStringLiteral) {
            addStyle(styles.contentStyle(token.type), token.start, token.end)
            continue
        }
        val contentStart = token.start + 1
        val contentEnd = if (token.isTerminated) token.end - 1 else token.end
        val quoteStyle = styles.quoteStyle(token.type)
        addStyle(quoteStyle, token.start, contentStart)
        if (contentEnd > contentStart) {
            addStyle(styles.contentStyle(token.type), contentStart, contentEnd)
        }
        if (token.isTerminated) {
            addStyle(quoteStyle, contentEnd, token.end)
        }
    }
}

internal fun highlightedLineRange(
    scrollOffsetPx: Int,
    viewportHeightPx: Int,
    lineHeightPx: Int,
): IntRange {
    if (lineHeightPx <= 0) {
        return 0 until highlightChunkLines
    }
    val firstVisibleLine = (scrollOffsetPx / lineHeightPx).fastCoerceAtLeast(0)
    val lastVisibleLine = ((scrollOffsetPx + viewportHeightPx) / lineHeightPx)
        .fastCoerceAtLeast(firstVisibleLine)
    val firstChunk = firstVisibleLine / highlightChunkLines
    val lastChunk = lastVisibleLine / highlightChunkLines
    return firstChunk * highlightChunkLines until (lastChunk + 1) * highlightChunkLines
}
