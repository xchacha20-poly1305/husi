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
import okio.Buffer

class BinaryOutput {

    private val buffer = Buffer()

    fun toByteArray(): ByteArray = buffer.snapshot().toByteArray()

    fun writeByte(value: Byte) {
        buffer.writeByte(value.toInt())
    }

    fun writeByte(value: Int) {
        buffer.writeByte(value)
    }

    fun writeBytes(bytes: ByteArray) {
        buffer.write(bytes)
    }

    fun writeBoolean(value: Boolean) = writeByte(if (value) 1 else 0)

    fun writeInt(value: Int) {
        buffer.writeIntLe(value)
    }

    fun writeLong(value: Long) {
        buffer.writeLongLe(value)
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
        for (index in 0 until value.lastIndex) writeByte(value[index].code)
        writeByte(value[value.lastIndex].code or ASCII_TERMINATOR_BIT)
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
}
