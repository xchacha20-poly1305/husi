package fr.husi.io

internal object BinaryFormat {

    const val INT_BYTES = 4
    const val LONG_BYTES = 8

    const val VAR_INT_PAYLOAD_MASK = 0x7F
    const val VAR_INT_CONTINUATION_BIT = 0x80
    const val VAR_INT_PAYLOAD_BITS = 7

    /**
     * A "varint flag" spends bit 8 of its first byte on a boolean, so that byte carries only six
     * payload bits and uses bit 7 as its continuation bit. Later bytes are plain varint bytes.
     */
    const val VAR_INT_FLAG_PAYLOAD_MASK = 0x3F
    const val VAR_INT_FLAG_BIT = 0x80
    const val VAR_INT_FLAG_CONTINUATION_BIT = 0x40
    const val VAR_INT_FLAG_FIRST_SHIFT = 6

    /** A varint spends at most five bytes, the last one holding the top four bits. */
    const val VAR_INT_LAST_SHIFT = 28

    /** A varint flag spends at most five bytes too, but its shifts start at six instead of seven. */
    const val VAR_INT_FLAG_LAST_SHIFT = 27

    /** A lone `0x80`: an empty varint flag with the flag set, decoding to a length of zero. */
    const val NULL_STRING = 0x80

    /** A lone `0x81`: the same, with a length of one, which stands for the empty string. */
    const val EMPTY_STRING = 0x81

    /**
     * Strings of two to thirty-two pure-ASCII characters are written raw, without a length prefix,
     * and bit 8 of the last byte terminates them. A one character string is too short to be told
     * apart from a length prefix, so it takes the UTF-8 path instead.
     */
    const val ASCII_MIN_LENGTH = 2
    const val ASCII_MAX_LENGTH = 32
    const val ASCII_TERMINATOR_BIT = 0x80
    const val ASCII_MAX_CHAR = 0x7F

    /** UTF-8 is applied to UTF-16 code units, so a surrogate pair becomes two three byte sequences. */
    const val UTF8_ONE_BYTE_MAX = 0x007F
    const val UTF8_TWO_BYTE_MAX = 0x07FF
    const val UTF8_CONTINUATION_MASK = 0x3F
    const val UTF8_CONTINUATION_PREFIX = 0x80
    const val UTF8_TWO_BYTE_PREFIX = 0xC0
    const val UTF8_THREE_BYTE_PREFIX = 0xE0
    const val UTF8_TWO_BYTE_PAYLOAD_MASK = 0x1F
    const val UTF8_THREE_BYTE_PAYLOAD_MASK = 0x0F

    const val BYTE_MASK = 0xFF
}
