package fr.husi.ktx

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private val GO_DURATION_UNITS = mapOf(
    "ns" to 1.nanoseconds,
    "us" to 1.microseconds,
    "µs" to 1.microseconds, // U+00B5 micro sign
    "μs" to 1.microseconds, // U+03BC Greek letter mu
    "ms" to 1.milliseconds,
    "s" to 1.seconds,
    "m" to 1.minutes,
    "h" to 1.hours,
    "d" to 1.days, // sing-box extension
).mapValues { it.value.inWholeNanoseconds }

// One `[0-9]*(\.[0-9]*)?[unit]` component. Every group may be empty, so it matches at any index.
private val GO_DURATION_COMPONENT = Regex("""(\d*)(?:\.(\d*))?([^\d.]*)""")

// Fraction digits beyond this cannot change the nanosecond result and would overflow a Long.
private const val MAX_FRACTION_DIGITS = 18

/**
 * Parses a Go style duration such as `300ms`, `-1.5h` or `2h45m`, as sing-box accepts it
 * (which also allows the `d` unit for days).
 *
 * @throws IllegalArgumentException if [text] is not a valid duration.
 */
fun parseGoDuration(text: String): Duration {
    fun invalid(reason: String = "invalid duration"): Nothing {
        throw IllegalArgumentException("$reason \"$text\"")
    }

    val sign = text.firstOrNull()?.takeIf { it == '-' || it == '+' }
    val isNegative = sign == '-'
    val unsigned = if (sign == null) {
        text
    } else {
        text.substring(1)
    }
    if (unsigned == "0") {
        return Duration.ZERO
    }
    if (unsigned.isEmpty()) {
        invalid()
    }

    var totalNanoseconds = 0L
    var index = 0
    try {
        while (index < unsigned.length) {
            val match = GO_DURATION_COMPONENT.matchAt(unsigned, index)!!
            val (integerDigits, fractionDigits, unitName) = match.destructured
            if (integerDigits.isEmpty() && fractionDigits.isEmpty()) {
                invalid()
            }
            if (unitName.isEmpty()) {
                invalid("missing unit in duration")
            }
            val unitNanoseconds = GO_DURATION_UNITS[unitName]
                ?: invalid("unknown unit \"$unitName\" in duration")

            val integer = if (integerDigits.isEmpty()) 0L else integerDigits.toLong()
            var componentNanoseconds = Math.multiplyExact(integer, unitNanoseconds)
            if (fractionDigits.isNotEmpty()) {
                val keptDigits = fractionDigits.take(MAX_FRACTION_DIGITS)
                val fraction = keptDigits.toLong().toDouble()
                val scale = 10.0.pow(keptDigits.length)
                componentNanoseconds = Math.addExact(
                    componentNanoseconds,
                    (fraction * (unitNanoseconds / scale)).toLong(),
                )
            }
            totalNanoseconds = Math.addExact(totalNanoseconds, componentNanoseconds)
            index = match.range.last + 1
        }
    } catch (_: ArithmeticException) {
        invalid()
    } catch (_: NumberFormatException) {
        invalid()
    }

    val duration = totalNanoseconds.nanoseconds
    return if (isNegative) {
        -duration
    } else {
        duration
    }
}
