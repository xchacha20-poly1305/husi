package fr.husi.utils.appicon

internal object IcnsDecoder {
    private val headerMagic = byteArrayOf(0x69, 0x63, 0x6E, 0x73)
    private val pngMagic = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    private val preferredTypes = setOf("ic10", "ic09", "ic14", "ic13", "ic08", "ic07")

    fun extractBestPng(bytes: ByteArray): ByteArray? {
        if (bytes.size < 8) return null
        if (!bytes.regionMatches(0, headerMagic)) return null
        val totalLength = readIntBigEndian(bytes, 4)
        if (totalLength < 8 || totalLength > bytes.size) return null

        var offset = 8
        var bestPng: ByteArray? = null
        while (offset + 8 <= totalLength) {
            val type = bytes.decodeToString(startIndex = offset, endIndex = offset + 4)
            val chunkLength = readIntBigEndian(bytes, offset + 4)
            if (chunkLength < 8 || offset + chunkLength > totalLength) break
            val payload = bytes.copyOfRange(offset + 8, offset + chunkLength)
            if (type in preferredTypes && payload.regionMatches(0, pngMagic)) {
                if (bestPng == null || payload.size > bestPng.size) {
                    bestPng = payload
                }
            }
            offset += chunkLength
        }
        return bestPng
    }

    private fun readIntBigEndian(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF shl 24) or
            (bytes[offset + 1].toInt() and 0xFF shl 16) or
            (bytes[offset + 2].toInt() and 0xFF shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun ByteArray.regionMatches(offset: Int, expected: ByteArray): Boolean {
        if (offset < 0 || offset + expected.size > size) return false
        for (index in expected.indices) {
            if (this[offset + index] != expected[index]) return false
        }
        return true
    }
}
