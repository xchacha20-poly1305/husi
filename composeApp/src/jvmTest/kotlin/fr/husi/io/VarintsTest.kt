package fr.husi.io

import okio.Buffer
import okio.BufferedSink
import okio.ByteString.Companion.decodeHex
import okio.EOFException
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VarintsTest {

    @Test
    fun `writeLeb128 matches protobuf varints`() {
        assertEquals("00", encode { it.writeLeb128(0) })
        assertEquals("7f", encode { it.writeLeb128(127) })
        assertEquals("ac02", encode { it.writeLeb128(300) })
        assertEquals("ffffffffffffffffff01", encode { it.writeLeb128(-1) })
    }

    @Test
    fun `readLeb128 reads what writeLeb128 writes`() {
        for (value in listOf(0L, 1L, 300L, Int.MAX_VALUE.toLong(), Long.MAX_VALUE, -1L)) {
            assertEquals(value, Buffer().also { it.writeLeb128(value) }.readLeb128())
        }
    }

    @Test
    fun `readLeb128 rejects truncated and overlong input`() {
        assertFailsWith<EOFException> { decode("80").readLeb128() }
        assertFailsWith<IOException> { decode("ffffffffffffffffffff01").readLeb128() }
    }

    @Test
    fun `writeQuicVarint matches RFC 9000 examples`() {
        assertEquals("25", encode { it.writeQuicVarint(37) })
        assertEquals("7bbd", encode { it.writeQuicVarint(15293) })
        assertEquals("9d7f3e7d", encode { it.writeQuicVarint(494878333) })
        assertEquals("c2197c5eff14e88c", encode { it.writeQuicVarint(151288809941952652) })
    }

    /** From TrustTunnel's `deeplink/src/varint.rs` tests. */
    @Test
    fun `writeQuicVarint takes the shortest form on each side of a length boundary`() {
        assertEquals("3f", encode { it.writeQuicVarint(63) })
        assertEquals("4040", encode { it.writeQuicVarint(64) })
        assertEquals("43e8", encode { it.writeQuicVarint(1000) })
        assertEquals("7fff", encode { it.writeQuicVarint(16383) })
        assertEquals("80004000", encode { it.writeQuicVarint(16384) })
        assertEquals("bfffffff", encode { it.writeQuicVarint(1073741823) })
        assertEquals("c000000040000000", encode { it.writeQuicVarint(1073741824) })
        assertEquals("ffffffffffffffff", encode { it.writeQuicVarint(0x3FFFFFFFFFFFFFFFL) })
    }

    @Test
    fun `readQuicVarint rejects a truncated varint`() {
        assertFailsWith<EOFException> { decode("40").readQuicVarint() }
    }

    @Test
    fun `readQuicVarint reads what writeQuicVarint writes`() {
        for (value in listOf(0L, 63L, 64L, 16383L, 16384L, 1073741823L, 1073741824L, 0x3FFFFFFFFFFFFFFFL)) {
            assertEquals(value, Buffer().also { it.writeQuicVarint(value) }.readQuicVarint())
        }
        // RFC 9000 §A.1: a non-minimal encoding still decodes.
        assertEquals(37, decode("4025").readQuicVarint())
    }

    @Test
    fun `writeQuicVarint rejects values outside 62 bits`() {
        assertFailsWith<IllegalArgumentException> { Buffer().writeQuicVarint(-1) }
        assertFailsWith<IllegalArgumentException> { Buffer().writeQuicVarint(0x4000000000000000L) }
    }

    private fun encode(write: (BufferedSink) -> Unit): String = Buffer().also(write).readByteString().hex()

    private fun decode(hex: String): Buffer = Buffer().write(hex.decodeHex())
}
