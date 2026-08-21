package fr.husi.io

import fr.husi.fmt.BeanConverters
import fr.husi.ktx.b64EncodeOneLine
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regenerates `BinaryGolden.kt` from [BinaryFixtures].
 *
 * This is only a tool, so it stays disabled.
 * Run it only when a fixture is deliberately added.
 *
 * Drop the [Ignore] for one run, point [OUTPUT_VARIABLE] at the golden file and put it back:
 *
 * ```
 * HUSI_GOLDEN_OUT=$(realpath composeApp/src/commonTest/kotlin/fr/husi/io/BinaryGolden.kt) \
 *     ./gradlew :composeApp:desktopTest --tests fr.husi.io.BinaryGoldenGenerator --return
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
            val bytes = BeanConverters.serialize(fixture.build())

            val decoded = BeanConverters.deserialize(fixture.newInstance(), bytes)
            val reencoded = BeanConverters.serialize(decoded)
            if (!bytes.contentEquals(reencoded)) {
                unstable += fixture.name + " differs at byte " +
                    bytes.indices.first { it >= reencoded.size || bytes[it] != reencoded[it] }
            }

            builder.append("        Case(\n")
            builder.append("            name = ").append(quote(fixture.name)).append(",\n")
            builder.append("            base64 = ")
                .append(quote(bytes.b64EncodeOneLine())).append(",\n")
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
