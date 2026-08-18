package fr.husi.utils.appicon

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacPropertyListTest {
    private val infoPlistKeys = setOf("CFBundleDisplayName", "CFBundleName", "CFBundleIconFile")

    @Test
    fun `reads ascii strings out of a binary plist`() {
        val bytes = binaryPlistOf(
            listOf(
                "CFBundleName" to "Firefox",
                "CFBundleIconFile" to "firefox.icns",
            ),
        )

        val values = MacPropertyList.readTopLevelStrings(bytes, infoPlistKeys)

        assertEquals("Firefox", values["CFBundleName"])
        assertEquals("firefox.icns", values["CFBundleIconFile"])
        assertNull(values["CFBundleDisplayName"])
    }

    @Test
    fun `reads non ascii strings stored as UTF-16`() {
        val bytes = binaryPlistOf(listOf("CFBundleDisplayName" to "网易云音乐"))

        val values = MacPropertyList.readTopLevelStrings(bytes, infoPlistKeys)

        assertEquals("网易云音乐", values["CFBundleDisplayName"])
    }

    @Test
    fun `reads a dictionary whose entry count does not fit in the marker`() {
        val entries = (0 until 20).map { index -> "Key$index" to "Value$index" }
        val bytes = binaryPlistOf(entries)

        val values = MacPropertyList.readTopLevelStrings(bytes, setOf("Key0", "Key19"))

        assertEquals("Value0", values["Key0"])
        assertEquals("Value19", values["Key19"])
    }

    @Test
    fun `falls back to the XML format`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <plist version="1.0">
            <dict>
                <key>CFBundleName</key>
                <string>Firefox</string>
                <key>CFBundleIconFile</key>
                <string>firefox</string>
            </dict>
            </plist>
        """.trimIndent().toByteArray()

        val values = MacPropertyList.readTopLevelStrings(xml, infoPlistKeys)

        assertEquals("Firefox", values["CFBundleName"])
        assertEquals("firefox", values["CFBundleIconFile"])
    }

    @Test
    fun `returns nothing for damaged input instead of throwing`() {
        val truncated = binaryPlistOf(listOf("CFBundleName" to "Firefox")).copyOfRange(0, 20)

        assertTrue(MacPropertyList.readTopLevelStrings(truncated, infoPlistKeys).isEmpty())
        assertTrue(MacPropertyList.readTopLevelStrings(ByteArray(0), infoPlistKeys).isEmpty())
        assertTrue(MacPropertyList.readTopLevelStrings("not a plist".toByteArray(), infoPlistKeys).isEmpty())
    }

    /**
     * Writes a `bplist00` holding one flat dictionary of strings: the object table is the
     * dictionary followed by every key and then every value, and the trailer points back at
     * the offset table.
     */
    private fun binaryPlistOf(entries: List<Pair<String, String>>): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("bplist00".toByteArray(Charsets.US_ASCII))

        val offsets = ArrayList<Int>()
        fun startObject() = offsets.add(body.size())

        fun writeCount(marker: Int, count: Int) {
            if (count < 0x0F) {
                body.write((marker shl 4) or count)
                return
            }
            body.write((marker shl 4) or 0x0F)
            // An extended count is an integer object: 0x10 marks a single byte integer.
            body.write(0x10)
            body.write(count)
        }

        startObject()
        writeCount(0xD, entries.size)
        // One byte per reference: the dictionary is object 0, keys and values follow it.
        for (index in entries.indices) body.write(1 + index)
        for (index in entries.indices) body.write(1 + entries.size + index)

        for (text in entries.map { it.first } + entries.map { it.second }) {
            startObject()
            if (text.all { it.code < 0x80 }) {
                writeCount(0x5, text.length)
                body.write(text.toByteArray(Charsets.US_ASCII))
            } else {
                val encoded = text.toByteArray(Charsets.UTF_16BE)
                writeCount(0x6, encoded.size / 2)
                body.write(encoded)
            }
        }

        val offsetTableOffset = body.size()
        for (offset in offsets) {
            body.write(offset shr 8)
            body.write(offset and 0xFF)
        }

        val trailer = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
        trailer.position(6)
        trailer.put(2) // offset int size
        trailer.put(1) // object reference size
        trailer.putLong(offsets.size.toLong())
        trailer.putLong(0L) // root object
        trailer.putLong(offsetTableOffset.toLong())
        body.write(trailer.array())

        return body.toByteArray()
    }
}
