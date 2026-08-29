package fr.husi.io

import fr.husi.fmt.BeanConverters
import fr.husi.ktx.b64EncodeOneLine
import java.io.File
import java.util.Base64
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Rewrites `BinaryGolden.kt` from [BinaryFixtures].
 *
 * Recorded payloads are history and are never re-encoded: a fixture that already has a golden case
 * keeps its bytes, and only a newly added fixture is encoded from scratch. The snapshots are read
 * back out of those same bytes, so running this tool cannot rewrite what an earlier release
 * recorded — it can only append to it.
 *
 * This is only a tool, so it stays disabled.
 * Run it only when a fixture is deliberately added.
 *
 * Drop the [Ignore] for one run, point [OUTPUT_VARIABLE] at the golden file and put it back:
 *
 * ```
 * HUSI_GOLDEN_OUT=$(realpath composeApp/src/commonTest/kotlin/fr/husi/io/BinaryGolden.kt) \
 *     ./gradlew :composeApp:desktopTest --tests fr.husi.io.BinaryGoldenGenerator --rerun
 * ```
 */
@Ignore
class BinaryGoldenGenerator {

    @Test
    fun generate() {
        val builder = StringBuilder()
        val unstable = mutableListOf<String>()
        builder.append(HEADER)
        for (fixture in BinaryFixtures.fixtures) {
            val recorded = BinaryGolden.cases.firstOrNull { it.name == fixture.name }
            val bytes = recorded
                ?.let { Base64.getDecoder().decode(it.base64) }
                ?: BeanConverters.serialize(fixture.build())

            val decoded = BeanConverters.deserialize(fixture.newInstance(), bytes)
            val reencoded = BeanConverters.serialize(decoded)
            if (!bytes.contentEquals(reencoded)) {
                unstable += fixture.name + " differs at byte " +
                    bytes.indices.first { it >= reencoded.size || bytes[it] != reencoded[it] }
            }

            builder.append("        Case(\n")
            builder.append("            name = ").append(quote(fixture.name)).append(",\n")
            builder.append("            base64 = ")
                .append(quote(recorded?.base64 ?: bytes.b64EncodeOneLine()))
                .append(",\n")
            builder.append("            snapshot = ")
                .append(quote(BinaryFixtures.snapshot(decoded))).append(",\n")
            builder.append("            truncatedSnapshots = listOf(\n")
            for (length in BinaryGolden.truncationLengths(bytes.size)) {
                val truncated = BeanConverters.deserialize(
                    fixture.newInstance(),
                    bytes.copyOf(length),
                )
                builder.append("                ")
                    .append(quote(BinaryFixtures.snapshot(truncated))).append(",\n")
            }
            builder.append("            ),\n")
            builder.append("        ),\n")
        }
        builder.append(FOOTER)

        val destination = File(System.getenv(OUTPUT_VARIABLE) ?: DEFAULT_OUTPUT)
        destination.parentFile?.mkdirs()
        destination.writeText(builder.toString())
        println("Wrote " + destination.absolutePath)
        assertTrue(
            unstable.isEmpty(),
            "Fixtures do not survive a decode/encode round trip: $unstable",
        )
    }

    private fun quote(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "\"" + escaped + "\""
    }

    private companion object {
        const val OUTPUT_VARIABLE = "HUSI_GOLDEN_OUT"
        const val DEFAULT_OUTPUT = "build/BinaryGolden.kt"

        val HEADER = """
            package fr.husi.io

            /**
             * Payloads recorded by an earlier release, next to what they decode to.
             *
             * Frozen data: a case is appended, never rewritten. See [BinaryGoldenGenerator].
             */
            object BinaryGolden {

                class Case(
                    val name: String,
                    val base64: String,
                    val snapshot: String,
                    val truncatedSnapshots: List<String>,
                )

                fun truncationLengths(size: Int): List<Int> =
                    listOf(0, 1, 5, 13, size / 4, size / 2, size * 3 / 4)
                        .filter { it < size }
                        .distinct()
                        .sorted()

                val cases: List<Case> = listOf(

        """.trimIndent()

        val FOOTER = """
                )
            }

        """.trimIndent()
    }
}
