package fr.husi.utils.appicon

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class IcnsDecoderTest {
    @Test
    fun `picks the largest PNG payload among preferred types`() {
        val small = pngBytes(1)
        val large = pngBytes(4)
        val bytes = icnsOf(
            listOf(
                "ic07" to small,
                "ic10" to large,
            ),
        )

        val extracted = IcnsDecoder.extractBestPng(bytes)
        assertContentEquals(large, extracted)
    }

    @Test
    fun `returns null when preferred chunks are not PNG`() {
        val bytes = icnsOf(
            listOf("ic08" to byteArrayOf(0x00, 0x01, 0x02, 0x03)),
        )
        assertNull(IcnsDecoder.extractBestPng(bytes))
    }

    @Test
    fun `returns null for a truncated header`() {
        assertNull(IcnsDecoder.extractBestPng("icns".toByteArray()))
    }

    private fun pngBytes(size: Int): ByteArray {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFFFF0000.toInt())
        val stream = ByteArrayOutputStream()
        ImageIO.write(image, "png", stream)
        return stream.toByteArray()
    }

    private fun icnsOf(chunks: List<Pair<String, ByteArray>>): ByteArray {
        val payloadSize = chunks.sumOf { 8 + it.second.size }
        val total = 8 + payloadSize
        val buffer = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        buffer.put("icns".toByteArray(Charsets.US_ASCII))
        buffer.putInt(total)
        for ((type, data) in chunks) {
            buffer.put(type.toByteArray(Charsets.US_ASCII))
            buffer.putInt(8 + data.size)
            buffer.put(data)
        }
        return buffer.array()
    }
}
