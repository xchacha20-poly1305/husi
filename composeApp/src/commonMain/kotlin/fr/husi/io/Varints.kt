package fr.husi.io

import okio.BufferedSink
import okio.BufferedSource
import okio.IOException

private const val LEB128_PAYLOAD_MASK = 0x7F
private const val LEB128_CONTINUATION_BIT = 0x80
private const val LEB128_PAYLOAD_BITS = 7
private const val LEB128_LAST_SHIFT = 63

fun BufferedSink.writeLeb128(value: Long) {
    var remaining = value
    while (remaining ushr LEB128_PAYLOAD_BITS != 0L) {
        writeByte((remaining.toInt() and LEB128_PAYLOAD_MASK) or LEB128_CONTINUATION_BIT)
        remaining = remaining ushr LEB128_PAYLOAD_BITS
    }
    writeByte(remaining.toInt())
}

fun BufferedSource.readLeb128(): Long {
    var value = 0L
    var shift = 0
    while (shift <= LEB128_LAST_SHIFT) {
        val byte = readByte().toInt()
        value = value or ((byte and LEB128_PAYLOAD_MASK).toLong() shl shift)
        if (byte and LEB128_CONTINUATION_BIT == 0) {
            return value
        }
        shift += LEB128_PAYLOAD_BITS
    }
    throw IOException("LEB128 varint longer than 64 bits")
}

private const val QUIC_VARINT_ONE_BYTE_MAX = 0x3FL
private const val QUIC_VARINT_TWO_BYTE_MAX = 0x3FFFL
private const val QUIC_VARINT_FOUR_BYTE_MAX = 0x3FFFFFFFL
private const val QUIC_VARINT_MAX = 0x3FFFFFFFFFFFFFFFL

private const val QUIC_VARINT_TWO_BYTE_PREFIX = 0x4000L
private const val QUIC_VARINT_FOUR_BYTE_PREFIX = 0x80000000L
private const val QUIC_VARINT_EIGHT_BYTE_PREFIX = -0x4000000000000000L

private const val QUIC_VARINT_SIZE_SHIFT = 6
private const val QUIC_VARINT_FIRST_PAYLOAD_MASK = 0x3F
private const val BYTE_MASK = 0xFF

fun BufferedSink.writeQuicVarint(value: Long) {
    require(value in 0..QUIC_VARINT_MAX) { "QUIC varint out of range: $value" }
    when {
        value <= QUIC_VARINT_ONE_BYTE_MAX -> writeByte(value.toInt())
        value <= QUIC_VARINT_TWO_BYTE_MAX -> writeShort((value or QUIC_VARINT_TWO_BYTE_PREFIX).toInt())
        value <= QUIC_VARINT_FOUR_BYTE_MAX -> writeInt((value or QUIC_VARINT_FOUR_BYTE_PREFIX).toInt())
        else -> writeLong(value or QUIC_VARINT_EIGHT_BYTE_PREFIX)
    }
}

fun BufferedSource.readQuicVarint(): Long {
    val first = readByte().toInt() and BYTE_MASK
    val byteCount = 1 shl (first ushr QUIC_VARINT_SIZE_SHIFT)
    var value = (first and QUIC_VARINT_FIRST_PAYLOAD_MASK).toLong()
    repeat(byteCount - 1) {
        value = (value shl Byte.SIZE_BITS) or (readByte().toLong() and BYTE_MASK.toLong())
    }
    return value
}
