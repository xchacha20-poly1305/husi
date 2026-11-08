package fr.husi.ui.dashboard

import androidx.compose.runtime.Immutable
import fr.husi.ui.StringOrRes

@Immutable
data class IPProbeState(
    val isDoing: Boolean = false,
    val ip: String? = null,
    val countryCode: String = "",
    val error: StringOrRes? = null,
)

fun countryEmoji(code: String): String? {
    val normalized = code.uppercase()
    if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) {
        return null
    }
    return buildString {
        for (letter in normalized) {
            val value = 0x1F1E6 + (letter - 'A') - 0x10000
            append((0xD800 + value / 1024).toChar())
            append((0xDC00 + value % 1024).toChar())
        }
    }
}
