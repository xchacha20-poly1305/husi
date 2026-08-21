package fr.husi.io

import fr.husi.io.BinaryFormat.ASCII_TERMINATOR_BIT
import fr.husi.io.BinaryFormat.BYTE_MASK
import fr.husi.io.BinaryFormat.UTF8_CONTINUATION_MASK
import fr.husi.io.BinaryFormat.UTF8_THREE_BYTE_PAYLOAD_MASK
import fr.husi.io.BinaryFormat.UTF8_TWO_BYTE_PAYLOAD_MASK
import fr.husi.io.BinaryFormat.VAR_INT_CONTINUATION_BIT
import fr.husi.io.BinaryFormat.VAR_INT_FLAG_BIT
import fr.husi.io.BinaryFormat.VAR_INT_FLAG_CONTINUATION_BIT
import fr.husi.io.BinaryFormat.VAR_INT_FLAG_FIRST_SHIFT
import fr.husi.io.BinaryFormat.VAR_INT_FLAG_LAST_SHIFT
import fr.husi.io.BinaryFormat.VAR_INT_FLAG_PAYLOAD_MASK
import fr.husi.io.BinaryFormat.VAR_INT_LAST_SHIFT
import fr.husi.io.BinaryFormat.VAR_INT_PAYLOAD_BITS
import fr.husi.io.BinaryFormat.VAR_INT_PAYLOAD_MASK

class BinaryInput(private val bytes: ByteArray) {

    private var position = 0

    fun readByte(): Byte {
        require(1)
        return bytes[position++]
    }

    fun readBytes(length: Int): ByteArray {
        require(length)
        val result = bytes.copyOfRange(position, position + length)
        position += length
        return result
    }

    fun readBoolean(): Boolean = readByte() == ONE

    fun readInt(): Int {
        require(BinaryFormat.INT_BYTES)
        var result = 0
        for (shift in 0 until BinaryFormat.INT_BYTES * Byte.SIZE_BITS step Byte.SIZE_BITS) {
            result = result or ((bytes[position++].toInt() and BYTE_MASK) shl shift)
        }
        return result
    }

    fun readLong(): Long {
        require(BinaryFormat.LONG_BYTES)
        var result = 0L
        for (shift in 0 until BinaryFormat.LONG_BYTES * Byte.SIZE_BITS step Byte.SIZE_BITS) {
            result = result or ((bytes[position++].toLong() and BYTE_MASK.toLong()) shl shift)
        }
        return result
    }

    fun readVarInt(optimizePositive: Boolean): Int {
        var byte = readUnsignedByte()
        var result = byte and VAR_INT_PAYLOAD_MASK
        var shift = VAR_INT_PAYLOAD_BITS
        while (byte and VAR_INT_CONTINUATION_BIT != 0 && shift <= VAR_INT_LAST_SHIFT) {
            byte = readUnsignedByte()
            result = result or ((byte and VAR_INT_PAYLOAD_MASK) shl shift)
            shift += VAR_INT_PAYLOAD_BITS
        }
        return if (optimizePositive) result else unZigzag(result)
    }

    fun readVarIntFlag(optimizePositive: Boolean): Int {
        var byte = readUnsignedByte()
        var result = byte and VAR_INT_FLAG_PAYLOAD_MASK
        if (byte and VAR_INT_FLAG_CONTINUATION_BIT != 0) {
            var shift = VAR_INT_FLAG_FIRST_SHIFT
            while (true) {
                byte = readUnsignedByte()
                result = result or ((byte and VAR_INT_PAYLOAD_MASK) shl shift)
                if (byte and VAR_INT_CONTINUATION_BIT == 0) break
                if (shift == VAR_INT_FLAG_LAST_SHIFT) break
                shift += VAR_INT_PAYLOAD_BITS
            }
        }
        return if (optimizePositive) result else unZigzag(result)
    }

    fun readString(): String = readNullableString().orEmpty()

    fun readNullableString(): String? {
        if (!peekFlagBit()) return readTerminatedAscii()
        return when (val charCount = readVarIntFlag(true)) {
            NULL_LENGTH -> null
            EMPTY_LENGTH -> ""
            else -> readUtf8(charCount - 1)
        }
    }

    private fun peekFlagBit(): Boolean {
        require(1)
        return bytes[position].toInt() and VAR_INT_FLAG_BIT != 0
    }

    private fun readTerminatedAscii(): String {
        val builder = StringBuilder()
        while (true) {
            val byte = readUnsignedByte()
            if (byte and ASCII_TERMINATOR_BIT != 0) {
                builder.append((byte and ASCII_TERMINATOR_BIT.inv()).toChar())
                return builder.toString()
            }
            builder.append(byte.toChar())
        }
    }

    private fun readUtf8(charCount: Int): String {
        val builder = StringBuilder(charCount)
        repeat(charCount) {
            val byte = readUnsignedByte()
            val char = when (byte shr 4) {
                in ONE_BYTE_LEAD -> byte

                in TWO_BYTE_LEAD -> ((byte and UTF8_TWO_BYTE_PAYLOAD_MASK) shl 6) or
                    (readUnsignedByte() and UTF8_CONTINUATION_MASK)

                THREE_BYTE_LEAD -> ((byte and UTF8_THREE_BYTE_PAYLOAD_MASK) shl 12) or
                    ((readUnsignedByte() and UTF8_CONTINUATION_MASK) shl 6) or
                    (readUnsignedByte() and UTF8_CONTINUATION_MASK)

                else -> throw BinaryFormatException("Not a UTF-8 lead byte: $byte")
            }
            builder.append(char.toChar())
        }
        return builder.toString()
    }

    private fun readUnsignedByte(): Int = readByte().toInt() and BYTE_MASK

    private fun unZigzag(value: Int): Int = (value ushr 1) xor -(value and 1)

    private fun require(count: Int) {
        if (position + count > bytes.size) {
            throw BinaryUnderflowException(
                "Need $count bytes at $position but only ${bytes.size} are available",
            )
        }
    }

    private companion object {
        const val ONE = 1.toByte()

        const val NULL_LENGTH = 0
        const val EMPTY_LENGTH = 1

        val ONE_BYTE_LEAD = 0..7
        val TWO_BYTE_LEAD = 12..13
        const val THREE_BYTE_LEAD = 14
    }
}
