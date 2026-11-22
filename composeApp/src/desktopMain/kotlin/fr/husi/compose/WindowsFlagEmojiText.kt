package fr.husi.compose

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.platform.Font
import java.lang.Character.charCount
import java.lang.Character.codePointAt

object WindowsFlagEmojiText : FlagEmojiTextStyler {
    private val regionalIndicators = 0x1F1E6..0x1F1FF
    private val tagLetters = 0xE0061..0xE007A
    private const val BLACK_FLAG = 0x1F3F4
    private const val CANCEL_TAG = 0xE007F
    private const val SUBDIVISION_TAG_LENGTH = 5

    private val flagStyle = SpanStyle(
        fontFamily = FontFamily(Font("TwemojiFlags.ttf")),
        fontSynthesis = FontSynthesis.None,
    )

    override val outputTransformation = OutputTransformation {
        asCharSequence().forEachFlag { start, end ->
            addStyle(flagStyle, start, end)
        }
    }

    override fun style(text: CharSequence): AnnotatedString? {
        var result: AnnotatedString.Builder? = null
        text.forEachFlag { start, end ->
            val builder = result ?: AnnotatedString.Builder().also {
                it.append(text)
                result = it
            }
            builder.addStyle(flagStyle, start, end)
        }
        return result?.toAnnotatedString()
    }

    private inline fun CharSequence.forEachFlag(action: (start: Int, end: Int) -> Unit) {
        var start = 0
        while (start < length) {
            val codePoint = codePointAt(this, start)
            val next = start + charCount(codePoint)
            val end = when (codePoint) {
                in regionalIndicators if next < length && codePointAt(this, next) in regionalIndicators ->
                    next + charCount(codePoint)

                BLACK_FLAG -> subdivisionFlagEnd(next)

                else -> null
            }
            if (end != null) {
                action(start, end)
            }
            start = end ?: next
        }
    }

    private fun CharSequence.subdivisionFlagEnd(start: Int): Int? {
        var index = start
        repeat(SUBDIVISION_TAG_LENGTH) {
            if (index >= length) {
                return null
            }
            val tag = codePointAt(this, index)
            if (tag !in tagLetters) {
                return null
            }
            index += charCount(tag)
        }
        if (index >= length || codePointAt(this, index) != CANCEL_TAG) {
            return null
        }
        return index + charCount(CANCEL_TAG)
    }

}
