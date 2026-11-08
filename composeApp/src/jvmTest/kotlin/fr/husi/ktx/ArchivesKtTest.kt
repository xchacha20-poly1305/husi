package fr.husi.ktx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArchivesKtTest {

    private val destination: File = createTempDirectory("husi-archives").toFile()

    @AfterTest
    fun tearDown() {
        destination.deleteRecursively()
    }

    private fun unpack(archive: ByteArray) = unpackArchive(ByteArrayInputStream(archive), destination)

    private fun unpackedFiles(): Map<String, String> =
        destination.listFiles().orEmpty().associate { it.name to it.readText() }

    @Test
    fun `tar gz entries land flat in the destination`() {
        val archive = tarGz {
            directory("rule-set/")
            file("rule-set/geosite-cn.srs", "cn")
            file("geoip-cn.srs", "ip")
        }

        unpack(archive)

        assertEquals(mapOf("geosite-cn.srs" to "cn", "geoip-cn.srs" to "ip"), unpackedFiles())
    }

    @Test
    fun `tar gz takes long names from PAX and GNU records`() {
        val longDirectory = "sing-geosite-${"x".repeat(120)}/"
        val archive = tarGz {
            paxGlobalHeader("comment=github")
            paxPath("${longDirectory}geosite-google.srs")
            file("truncated-name", "google")
            gnuLongName("${longDirectory}geosite-apple.srs")
            file("truncated-name", "apple")
        }

        unpack(archive)

        assertEquals(mapOf("geosite-google.srs" to "google", "geosite-apple.srs" to "apple"), unpackedFiles())
    }

    @Test
    fun `zip entries land flat in the destination`() {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry("sing-box/"))
            zip.putNextEntry(ZipEntry("sing-box/geoip-us.srs"))
            zip.write("us".encodeToByteArray())
        }

        unpack(buffer.toByteArray())

        assertEquals(mapOf("geoip-us.srs" to "us"), unpackedFiles())
    }

    @Test
    fun `entries that name no file are skipped`() {
        unpack(tarGz { file("rules/..", "escape") })

        assertEquals(emptyMap(), unpackedFiles())
    }

    @Test
    fun `unknown format is rejected`() {
        assertFailsWith<IOException> { unpack("not an archive".encodeToByteArray()) }
    }

    private fun tarGz(build: TarBuilder.() -> Unit): ByteArray {
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { gzip ->
            gzip.write(TarBuilder().apply(build).finish())
        }
        return buffer.toByteArray()
    }

    /** Writes just the ustar fields the reader looks at. */
    private class TarBuilder {
        private val output = ByteArrayOutputStream()

        fun file(name: String, content: String) = entry(name, '0', content.encodeToByteArray())
        fun directory(name: String) = entry(name, '5', ByteArray(0))
        fun gnuLongName(name: String) = entry("././@LongLink", 'L', "$name\u0000".encodeToByteArray())
        fun paxPath(path: String) = entry("PaxHeader", 'x', paxRecord("path=$path"))
        fun paxGlobalHeader(record: String) = entry("pax_global_header", 'g', paxRecord(record))

        fun finish(): ByteArray {
            output.write(ByteArray(BLOCK_SIZE * 2))
            return output.toByteArray()
        }

        private fun entry(name: String, type: Char, content: ByteArray) {
            val header = ByteArray(BLOCK_SIZE)
            name.encodeToByteArray().take(100).toByteArray().copyInto(header, 0)
            "%011o\u0000".format(content.size).encodeToByteArray().copyInto(header, 124)
            header[156] = type.code.toByte()
            "ustar\u0000".encodeToByteArray().copyInto(header, 257)
            output.write(header)
            output.write(content)
            output.write(ByteArray((BLOCK_SIZE - content.size % BLOCK_SIZE) % BLOCK_SIZE))
        }

        /** `<length> <key>=<value>\n`, where the length counts its own digits. */
        private fun paxRecord(keyValue: String): ByteArray {
            val body = " $keyValue\n"
            var length = body.length + 1
            while ("$length$body".length != length) length++
            return "$length$body".encodeToByteArray()
        }

        companion object {
            private const val BLOCK_SIZE = 512
        }
    }
}
