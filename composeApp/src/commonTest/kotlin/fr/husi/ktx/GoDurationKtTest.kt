package fr.husi.ktx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class GoDurationKtTest {

    @Test
    fun `parseGoDuration should parse single units`() {
        assertEquals(30.seconds, parseGoDuration("30s"))
        assertEquals(1.minutes, parseGoDuration("1m"))
        assertEquals(1.hours, parseGoDuration("1h"))
        assertEquals(1.days, parseGoDuration("1d"))
        assertEquals(300.milliseconds, parseGoDuration("300ms"))
        assertEquals(5.microseconds, parseGoDuration("5us"))
        assertEquals(5.microseconds, parseGoDuration("5µs"))
        assertEquals(5.microseconds, parseGoDuration("5μs"))
        assertEquals(7.nanoseconds, parseGoDuration("7ns"))
    }

    @Test
    fun `parseGoDuration should keep nanosecond precision for fractions`() {
        assertEquals(30_500.milliseconds, parseGoDuration("30.5s"))
        assertEquals(30_000_005.microseconds, parseGoDuration("30.000005s"))
        assertEquals(30_000_000_005.nanoseconds, parseGoDuration("30.000000005s"))
        assertEquals(90.minutes, parseGoDuration("1.5h"))
        assertEquals(500.milliseconds, parseGoDuration(".5s"))
        assertEquals(1.seconds, parseGoDuration("1.s"))
    }

    @Test
    fun `parseGoDuration should sum multiple components`() {
        assertEquals(2.hours + 45.minutes, parseGoDuration("2h45m"))
        assertEquals(2.seconds, parseGoDuration("1s1s"))
    }

    @Test
    fun `parseGoDuration should apply sign`() {
        assertEquals((-30).seconds, parseGoDuration("-30s"))
        assertEquals((-90).minutes, parseGoDuration("-1.5h"))
        assertEquals(30.seconds, parseGoDuration("+30s"))
    }

    @Test
    fun `parseGoDuration should accept bare zero`() {
        assertEquals(Duration.ZERO, parseGoDuration("0"))
        assertEquals(Duration.ZERO, parseGoDuration("-0"))
        assertEquals(Duration.ZERO, parseGoDuration("0s"))
    }

    @Test
    fun `parseGoDuration should reject invalid input`() {
        for (text in listOf("", "-", "invalid", "30", ".s", "-.s", "1x", "1s2", "1 s", "1.2.3s")) {
            assertFailsWith<IllegalArgumentException>(text) { parseGoDuration(text) }
        }
    }

    @Test
    fun `parseGoDuration should reject overflow`() {
        assertFailsWith<IllegalArgumentException> { parseGoDuration("9999999999999999999ns") }
        assertFailsWith<IllegalArgumentException> { parseGoDuration("3000000h") }
    }
}
