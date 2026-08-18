package fr.husi.utils.appicon

import org.w3c.dom.Element
import java.io.File
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory

internal object MacPropertyList {
    private val binaryMagic = "bplist00".toByteArray(Charsets.US_ASCII)

    fun readTopLevelStrings(file: File, keys: Set<String>): Map<String, String> {
        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            return emptyMap()
        }
        return readTopLevelStrings(bytes, keys)
    }

    fun readTopLevelStrings(bytes: ByteArray, keys: Set<String>): Map<String, String> {
        if (keys.isEmpty()) return emptyMap()
        if (bytes.startsWith(binaryMagic)) {
            return try {
                BinaryPropertyList(bytes).readRootStrings(keys)
            } catch (_: Exception) {
                emptyMap()
            }
        }
        return readXmlStrings(bytes.decodeToString(), keys)
    }

    private val xmlFactory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }

    internal fun readXmlStrings(xml: String, keys: Set<String>): Map<String, String> {
        val doc = try {
            xmlFactory.newDocumentBuilder().parse(xml.byteInputStream())
        } catch (_: Exception) {
            return emptyMap()
        }

        val dict = doc.getElementsByTagName("dict").item(0) as? Element ?: return emptyMap()
        val children = dict.childNodes
        val result = LinkedHashMap<String, String>()

        var i = 0
        while (i < children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == "key") {
                val key = node.textContent.trim()
                var j = i + 1
                while (j < children.length && children.item(j) !is Element) j++
                val value = children.item(j) as? Element
                if (key in keys && value != null && value.tagName == "string") {
                    val text = value.textContent.trim()
                    if (text.isNotBlank()) result[key] = text
                }
                i = j + 1
            } else {
                i++
            }
        }
        return result
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (index in prefix.indices) {
            if (this[index] != prefix[index]) return false
        }
        return true
    }
}

private class BinaryPropertyList(private val bytes: ByteArray) {

    private companion object {
        const val HEADER_SIZE = 8
        const val TRAILER_SIZE = 32
        const val EXTENDED_COUNT = 0x0F
        const val INTEGER_MARKER = 0x1
        const val ASCII_STRING_MARKER = 0x5
        const val UTF16_STRING_MARKER = 0x6
        const val DICTIONARY_MARKER = 0xD
    }

    private val offsetIntSize: Int
    private val objectRefSize: Int
    private val objectCount: Int
    private val rootObject: Int
    private val offsetTableOffset: Int

    init {
        val trailer = bytes.size - TRAILER_SIZE
        require(trailer >= HEADER_SIZE) { "Property list is too short" }
        offsetIntSize = bytes[trailer + 6].toInt() and 0xFF
        objectRefSize = bytes[trailer + 7].toInt() and 0xFF
        require(offsetIntSize in 1..8 && objectRefSize in 1..8) { "Invalid reference widths" }
        objectCount = readUnsigned(trailer + 8, 8)
        rootObject = readUnsigned(trailer + 16, 8)
        offsetTableOffset = readUnsigned(trailer + 24, 8)
        require(rootObject < objectCount) { "Root object is out of range" }
        require(offsetTableOffset + objectCount * offsetIntSize <= trailer) {
            "Offset table is out of range"
        }
    }

    fun readRootStrings(keys: Set<String>): Map<String, String> {
        val root = offsetOf(rootObject)
        if (markerOf(root) != DICTIONARY_MARKER) return emptyMap()
        val (entryCount, contentOffset) = readCount(root)
        val result = LinkedHashMap<String, String>()
        for (index in 0 until entryCount) {
            val keyReference = readUnsigned(contentOffset + index * objectRefSize, objectRefSize)
            val key = readString(keyReference) ?: continue
            if (key !in keys) continue
            val valueOffset = contentOffset + (entryCount + index) * objectRefSize
            val value = readString(readUnsigned(valueOffset, objectRefSize)) ?: continue
            result[key] = value
        }
        return result
    }

    private fun readString(objectReference: Int): String? {
        if (objectReference >= objectCount) return null
        val objectOffset = offsetOf(objectReference)
        val (length, contentOffset) = readCount(objectOffset)
        return when (markerOf(objectOffset)) {
            ASCII_STRING_MARKER -> decode(contentOffset, length, Charsets.US_ASCII)
            UTF16_STRING_MARKER -> decode(contentOffset, length * 2, Charsets.UTF_16BE)
            else -> null
        }
    }

    private fun decode(offset: Int, byteLength: Int, charset: Charset): String? {
        if (offset < 0 || byteLength < 0 || offset + byteLength > bytes.size) return null
        return String(bytes, offset, byteLength, charset)
    }

    /**
     * @return the element count of the object at [objectOffset] and the offset its content
     * starts at. A low nibble of `0xF` means the count did not fit and is stored as an
     * integer object right after the marker.
     */
    private fun readCount(objectOffset: Int): Pair<Int, Int> {
        val lowNibble = bytes[objectOffset].toInt() and 0x0F
        if (lowNibble != EXTENDED_COUNT) {
            return lowNibble to objectOffset + 1
        }
        val countMarker = bytes[objectOffset + 1].toInt() and 0xFF
        require(countMarker shr 4 == INTEGER_MARKER) { "Extended count is not an integer" }
        val countSize = 1 shl (countMarker and 0x0F)
        val count = readUnsigned(objectOffset + 2, countSize)
        return count to objectOffset + 2 + countSize
    }

    private fun markerOf(objectOffset: Int): Int = (bytes[objectOffset].toInt() and 0xFF) shr 4

    private fun offsetOf(objectReference: Int): Int {
        return readUnsigned(offsetTableOffset + objectReference * offsetIntSize, offsetIntSize)
    }

    private fun readUnsigned(offset: Int, size: Int): Int {
        require(offset >= 0 && offset + size <= bytes.size) { "Read is out of range" }
        var value = 0L
        for (index in 0 until size) {
            value = (value shl 8) or (bytes[offset + index].toLong() and 0xFF)
        }
        require(value in 0..Int.MAX_VALUE) { "Value does not fit in an Int" }
        return value.toInt()
    }

}
