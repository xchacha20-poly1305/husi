package fr.husi.io

import fr.husi.io.BinaryFormat.ASCII_MAX_CHAR
import fr.husi.io.BinaryFormat.ASCII_MAX_LENGTH
import fr.husi.io.BinaryFormat.ASCII_MIN_LENGTH
import fr.husi.io.BinaryFormat.ASCII_TERMINATOR_BIT
import fr.husi.io.BinaryFormat.EMPTY_STRING
import fr.husi.io.BinaryFormat.NULL_STRING
import fr.husi.io.BinaryFormat.UTF8_CONTINUATION_MASK
import fr.husi.io.BinaryFormat.UTF8_CONTINUATION_PREFIX
import fr.husi.io.BinaryFormat.UTF8_ONE_BYTE_MAX
import fr.husi.io.BinaryFormat.UTF8_THREE_BYTE_PAYLOAD_MASK
import fr.husi.io.BinaryFormat.UTF8_THREE_BYTE_PREFIX
import fr.husi.io.BinaryFormat.UTF8_TWO_BYTE_MAX
import fr.husi.io.BinaryFormat.UTF8_TWO_BYTE_PAYLOAD_MASK
import fr.husi.io.BinaryFormat.UTF8_TWO_BYTE_PREFIX
import fr.husi.io.BinaryFormat.VAR_INT_CONTINUATION_BIT
import fr.husi.io.BinaryFormat.VAR_INT_FLAG_BIT
import fr.husi.io.BinaryFormat.VAR_INT_FLAG_CONTINUATION_BIT
import fr.husi.io.BinaryFormat.VAR_INT_FLAG_FIRST_SHIFT
import fr.husi.io.BinaryFormat.VAR_INT_FLAG_PAYLOAD_MASK
import fr.husi.io.BinaryFormat.VAR_INT_PAYLOAD_BITS
import fr.husi.io.BinaryFormat.VAR_INT_PAYLOAD_MASK

class BinaryOutput(initialCapacity: Int = DEFAULT_CAPACITY) {

    private var buffer = ByteArray(initialCapacity)
    private var size = 0

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    fun writeByte(value: Byte) {
        reserve(1)
        buffer[size++] = value
    }

    fun writeByte(value: Int) = writeByte(value.toByte())

    fun writeBytes(bytes: ByteArray) {
        reserve(bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun writeBoolean(value: Boolean) = writeByte(if (value) 1 else 0)

    fun writeInt(value: Int) {
        reserve(BinaryFormat.INT_BYTES)
        for (shift in 0 until BinaryFormat.INT_BYTES * Byte.SIZE_BITS step Byte.SIZE_BITS) {
            buffer[size++] = (value ushr shift).toByte()
        }
    }

    fun writeLong(value: Long) {
        reserve(BinaryFormat.LONG_BYTES)
        for (shift in 0 until BinaryFormat.LONG_BYTES * Byte.SIZE_BITS step Byte.SIZE_BITS) {
            buffer[size++] = (value ushr shift).toByte()
        }
    }

    fun writeVarInt(value: Int, optimizePositive: Boolean) {
        writeVarIntBytes(if (optimizePositive) value else zigzag(value))
    }

    fun writeVarIntFlag(flag: Boolean, value: Int, optimizePositive: Boolean) {
        val payload = if (optimizePositive) value else zigzag(value)
        var first = payload and VAR_INT_FLAG_PAYLOAD_MASK
        if (flag) first = first or VAR_INT_FLAG_BIT
        val remaining = payload ushr VAR_INT_FLAG_FIRST_SHIFT
        if (remaining == 0) {
            writeByte(first)
            return
        }
        writeByte(first or VAR_INT_FLAG_CONTINUATION_BIT)
        writeVarIntBytes(remaining)
    }

    private fun writeVarIntBytes(value: Int) {
        var remaining = value
        while (remaining ushr VAR_INT_PAYLOAD_BITS != 0) {
            writeByte((remaining and VAR_INT_PAYLOAD_MASK) or VAR_INT_CONTINUATION_BIT)
            remaining = remaining ushr VAR_INT_PAYLOAD_BITS
        }
        writeByte(remaining)
    }

    fun writeString(value: String?) {
        if (value == null) {
            writeByte(NULL_STRING)
            return
        }
        val charCount = value.length
        if (charCount == 0) {
            writeByte(EMPTY_STRING)
            return
        }
        if (charCount in ASCII_MIN_LENGTH..ASCII_MAX_LENGTH && value.all { it.code <= ASCII_MAX_CHAR }) {
            writeTerminatedAscii(value)
            return
        }
        // The length is stored as `charCount + 1`, leaving 0 for null and 1 for the empty string.
        writeVarIntFlag(true, charCount + 1, true)
        writeUtf8(value)
    }

    private fun writeTerminatedAscii(value: String) {
        for (char in value) writeByte(char.code)
        buffer[size - 1] = (buffer[size - 1].toInt() or ASCII_TERMINATOR_BIT).toByte()
    }

    private fun writeUtf8(value: String) {
        for (char in value) {
            val code = char.code
            when {
                code <= UTF8_ONE_BYTE_MAX -> writeByte(code)

                code > UTF8_TWO_BYTE_MAX -> {
                    writeByte(UTF8_THREE_BYTE_PREFIX or (code shr 12 and UTF8_THREE_BYTE_PAYLOAD_MASK))
                    writeByte(UTF8_CONTINUATION_PREFIX or (code shr 6 and UTF8_CONTINUATION_MASK))
                    writeByte(UTF8_CONTINUATION_PREFIX or (code and UTF8_CONTINUATION_MASK))
                }

                else -> {
                    writeByte(UTF8_TWO_BYTE_PREFIX or (code shr 6 and UTF8_TWO_BYTE_PAYLOAD_MASK))
                    writeByte(UTF8_CONTINUATION_PREFIX or (code and UTF8_CONTINUATION_MASK))
                }
            }
        }
    }

    private fun zigzag(value: Int): Int = (value shl 1) xor (value shr 31)

    private fun reserve(count: Int) {
        val required = size + count
        if (required <= buffer.size) return
        var capacity = buffer.size.coerceAtLeast(DEFAULT_CAPACITY)
        while (capacity < required) capacity *= 2
        buffer = buffer.copyOf(capacity)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 256
    }
}
