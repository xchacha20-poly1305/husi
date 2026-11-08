package fr.husi.io

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Align Kryo v5.6.2 */
class BinaryCodecTest {

    @Test
    fun `writeInt is four bytes little endian`() {
        assertBytes(encode { it.writeInt(1) }, 0x01, 0x00, 0x00, 0x00)
        assertBytes(encode { it.writeInt(-1) }, 0xFF, 0xFF, 0xFF, 0xFF)
        assertBytes(encode { it.writeInt(0x01020304) }, 0x04, 0x03, 0x02, 0x01)
        assertEquals(Int.MIN_VALUE, decode(encode { it.writeInt(Int.MIN_VALUE) }).readInt())
    }

    @Test
    fun `writeLong is eight bytes little endian`() {
        assertBytes(
            encode { it.writeLong(1L) },
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        assertBytes(
            encode { it.writeLong(0x0102030405060708L) },
            0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
        )
        assertEquals(Long.MIN_VALUE, decode(encode { it.writeLong(Long.MIN_VALUE) }).readLong())
    }

    @Test
    fun `writeBoolean is a single byte and only one decodes to true`() {
        assertBytes(encode { it.writeBoolean(true) }, 0x01)
        assertBytes(encode { it.writeBoolean(false) }, 0x00)
        assertEquals(false, decode(byteArrayOf(0x02)).readBoolean())
    }

    @Test
    fun `writeByte and writeBytes are verbatim`() {
        assertBytes(encode { it.writeByte(0x7F) }, 0x7F)
        assertBytes(encode { it.writeBytes(byteArrayOf(1, 2, 3)) }, 0x01, 0x02, 0x03)

        val input = decode(byteArrayOf(1, 2, 3))
        assertEquals(1, input.readByte())
        assertContentEquals(byteArrayOf(2, 3), input.readBytes(2))
    }

    @Test
    fun `writeVarInt grows one byte per seven bits`() {
        assertBytes(encode { it.writeVarInt(0, true) }, 0x00)
        assertBytes(encode { it.writeVarInt(127, true) }, 0x7F)
        assertBytes(encode { it.writeVarInt(128, true) }, 0x80, 0x01)
        assertBytes(encode { it.writeVarInt(1 shl 14, true) }, 0x80, 0x80, 0x01)
        assertBytes(encode { it.writeVarInt(1 shl 21, true) }, 0x80, 0x80, 0x80, 0x01)
        assertBytes(encode { it.writeVarInt(-1, true) }, 0xFF, 0xFF, 0xFF, 0xFF, 0x0F)
    }

    @Test
    fun `writeVarInt zigzags when positive values are not favoured`() {
        assertBytes(encode { it.writeVarInt(-1, false) }, 0x01)
        assertBytes(encode { it.writeVarInt(1, false) }, 0x02)
        assertBytes(encode { it.writeVarInt(-64, false) }, 0x7F)

        for (value in listOf(0, 1, -1, 63, -64, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals(value, decode(encode { it.writeVarInt(value, false) }).readVarInt(false))
            assertEquals(value, decode(encode { it.writeVarInt(value, true) }).readVarInt(true))
        }
    }

    @Test
    fun `writeVarIntFlag keeps the flag in bit eight and six payload bits in the first byte`() {
        assertBytes(encode { it.writeVarIntFlag(true, 0, true) }, 0x80)
        assertBytes(encode { it.writeVarIntFlag(false, 63, true) }, 0x3F)
        assertBytes(encode { it.writeVarIntFlag(true, 64, true) }, 0xC0, 0x01)
        assertBytes(encode { it.writeVarIntFlag(true, 1 shl 13, true) }, 0xC0, 0x80, 0x01)
        assertBytes(encode { it.writeVarIntFlag(true, 1 shl 20, true) }, 0xC0, 0x80, 0x80, 0x01)
        assertBytes(
            encode { it.writeVarIntFlag(true, 1 shl 27, true) },
            0xC0, 0x80, 0x80, 0x80, 0x01,
        )
    }

    @Test
    fun `writeString marks null and empty with a single byte`() {
        assertBytes(encode { it.writeString(null) }, 0x80)
        assertBytes(encode { it.writeString("") }, 0x81)
        assertNull(decode(byteArrayOf(0x80.toByte())).readNullableString())
        assertEquals("", decode(byteArrayOf(0x81.toByte())).readString())
    }

    @Test
    fun `writeString takes the unprefixed ASCII path for two to thirty two characters`() {
        // The trailing byte carries bit 8 as the terminator, there is no length prefix.
        assertBytes(encode { it.writeString("ab") }, 0x61, 0xE2)

        val longest = "x".repeat(ASCII_FAST_PATH_MAX_LENGTH)
        val encoded = encode { it.writeString(longest) }
        assertEquals(ASCII_FAST_PATH_MAX_LENGTH, encoded.size)
        assertEquals(0xF8.toByte(), encoded.last())
        assertEquals(longest, decode(encoded).readString())
    }

    @Test
    fun `writeString falls back to UTF-8 outside the ASCII fast path`() {
        // A single character is too short for the fast path.
        assertBytes(encode { it.writeString("a") }, 0x82, 0x61)

        val tooLong = "y".repeat(ASCII_FAST_PATH_MAX_LENGTH + 1)
        val encoded = encode { it.writeString(tooLong) }
        assertEquals(0xA2.toByte(), encoded.first())
        assertEquals(ASCII_FAST_PATH_MAX_LENGTH + 2, encoded.size)
        assertEquals(tooLong, decode(encoded).readString())
    }

    @Test
    fun `writeString encodes each UTF-16 unit on its own`() {
        assertBytes(encode { it.writeString("中") }, 0x82, 0xE4, 0xB8, 0xAD)
        assertBytes(encode { it.writeString("߿") }, 0x82, 0xDF, 0xBF)
        assertBytes(encode { it.writeString("ࠀ") }, 0x82, 0xE0, 0xA0, 0x80)

        // Surrogate pairs are written as two three byte sequences, not as one four byte one.
        assertBytes(
            encode { it.writeString("😀") },
            0x83, 0xED, 0xA0, 0xBD, 0xED, 0xB8, 0x80,
        )
        assertEquals("😀", decode(encode { it.writeString("😀") }).readString())
    }

    @Test
    fun `reading a source gives the same result as reading a byte array`() {
        val bytes = encode {
            it.writeInt(7)
            it.writeString("hello")
            it.writeString("😀")
        }

        val input = BinaryInput(Buffer().write(bytes))

        assertEquals(7, input.readInt())
        assertEquals("hello", input.readString())
        assertEquals("😀", input.readString())
    }

    @Test
    fun `toByteArray does not consume what was written`() {
        val output = BinaryOutput()
        output.writeString("kept")

        assertContentEquals(output.toByteArray(), output.toByteArray())

        output.writeInt(1)
        assertEquals("kept", decode(output.toByteArray()).readString())
    }

    @Test
    fun `reading past the end fails instead of returning garbage`() {
        assertFailsWith<BinaryUnderflowException> { decode(ByteArray(0)).readInt() }
        assertFailsWith<BinaryUnderflowException> { decode(byteArrayOf(1, 2, 3)).readInt() }
        assertFailsWith<BinaryUnderflowException> { decode(byteArrayOf(0x61)).readString() }
    }

    /** Strings of 2..32 pure-ASCII characters are written without a length prefix. */
    private val ASCII_FAST_PATH_MAX_LENGTH = 32

    private fun encode(block: (BinaryOutput) -> Unit): ByteArray {
        return BinaryOutput().also(block).toByteArray()
    }

    private fun decode(bytes: ByteArray) = BinaryInput(bytes)

    private fun assertBytes(actual: ByteArray, vararg expected: Int) {
        assertContentEquals(
            ByteArray(expected.size) { expected[it].toByte() },
            actual,
            actual.joinToString(" ") { "%02X".format(it) },
        )
    }
}
