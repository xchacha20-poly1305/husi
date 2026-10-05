package fr.husi.ktx

import androidx.compose.ui.util.fastCoerceIn
import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow

private const val DECIMAL_BASE = 1000.0
private const val BINARY_BASE = 1024.0

// Starts from kilo, so small values are shown as fractions of a kB instead of plain bytes.
private val KILO_UNIT_NAMES = listOf("kB", "MB", "GB", "TB", "PB", "EB")

/** Formats a byte count with decimal (1000-based) units, e.g. `1.5 MB`. */
fun Long.formatBytes(): String = formatKiloBytes(this, DECIMAL_BASE)

/** Formats a byte count with binary (1024-based) units but the same short unit names, e.g. `1.5 MB`. */
fun Long.formatMemoryBytes(): String = formatKiloBytes(this, BINARY_BASE)

private fun formatKiloBytes(bytes: Long, base: Double): String {
    if (bytes == 0L) {
        return "0 ${KILO_UNIT_NAMES.first()}"
    }
    val exponent = floor(ln(bytes.toDouble()) / ln(base))
        .toInt()
        .fastCoerceIn(1, KILO_UNIT_NAMES.size)
    val value = floor(bytes / base.pow(exponent) * 10 + 0.5) / 10
    val pattern = if (value < 10) {
        "%.1f %s"
    } else {
        "%.0f %s"
    }
    return String.format(Locale.ROOT, pattern, value, KILO_UNIT_NAMES[exponent - 1])
}
