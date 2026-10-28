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
            appendCodePoint(0x1F1E6 + (letter - 'A'))
        }
    }
}
