package fr.husi.io

import fr.husi.fmt.BeanConverters
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BinaryCompatibilityTest {

    @Test
    fun `decoding golden bytes yields the recorded fields`() {
        for (case in BinaryGolden.cases) {
            val fixture = fixtureOf(case.name)
            val decoded = BeanConverters.deserialize(fixture.newInstance(), decode(case.base64))
            assertEquals(case.snapshot, BinaryFixtures.snapshot(decoded), case.name)
        }
    }

    @Test
    fun `re-encoding golden bytes reproduces them exactly`() {
        for (case in BinaryGolden.cases) {
            val fixture = fixtureOf(case.name)
            val bytes = decode(case.base64)
            val decoded = BeanConverters.deserialize(fixture.newInstance(), bytes)
            assertContentEquals(bytes, BeanConverters.serialize(decoded), case.name)
        }
    }

    @Test
    fun `truncated payloads stop at the underflow and keep their defaults`() {
        for (case in BinaryGolden.cases) {
            val fixture = fixtureOf(case.name)
            val bytes = decode(case.base64)
            val lengths = BinaryGolden.truncationLengths(bytes.size)
            assertEquals(lengths.size, case.truncatedSnapshots.size, case.name)
            for ((index, length) in lengths.withIndex()) {
                val decoded = BeanConverters.deserialize(
                    fixture.newInstance(),
                    bytes.copyOf(length),
                )
                assertEquals(
                    case.truncatedSnapshots[index],
                    BinaryFixtures.snapshot(decoded),
                    case.name + " truncated to " + length,
                )
            }
        }
    }

    @Test
    fun `every fixture has a golden case`() {
        assertEquals(
            BinaryFixtures.fixtures.map { it.name },
            BinaryGolden.cases.map { it.name },
        )
    }

    private fun fixtureOf(name: String): BinaryFixtures.Fixture {
        return BinaryFixtures.fixtures.first { it.name == name }
    }

    private fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)
}
