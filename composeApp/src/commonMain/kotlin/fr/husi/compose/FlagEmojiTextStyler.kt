package fr.husi.compose

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.AnnotatedString

internal interface FlagEmojiTextStyler {
    val outputTransformation: OutputTransformation

    fun style(text: CharSequence): AnnotatedString?
}

internal val LocalFlagEmojiTextStyler = staticCompositionLocalOf<FlagEmojiTextStyler?> { null }
