package fr.husi.ktx

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

private val GZIP_MAGIC = byteArrayOf(0x1f, 0x8b.toByte())
private val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())

/**
 * Unpacks a tar.gz or zip [archive] into [destination], dropping the directory part of every
 * entry: each regular file lands directly in [destination] under its base name, replacing any
 * file of that name.
 */
fun unpackArchive(archive: File, destination: File) {
    archive.inputStream().use { unpackArchive(it, destination) }
}

/** Stream form of [unpackArchive]; the format is told by its magic bytes. */
fun unpackArchive(input: InputStream, destination: File) {
    val buffered = BufferedInputStream(input)
    buffered.mark(GZIP_MAGIC.size)
    val magic = buffered.readNBytes(GZIP_MAGIC.size)
    buffered.reset()
    destination.mkdirs()
    when {
        magic.contentEquals(GZIP_MAGIC) -> untarFlattened(GZIPInputStream(buffered), destination)
        magic.contentEquals(ZIP_MAGIC) -> unzipFlattened(ZipInputStream(buffered), destination)
        else -> throw IOException("not a tar.gz or zip archive")
    }
}

private fun unzipFlattened(input: ZipInputStream, destination: File) {
    while (true) {
        val entry = input.nextEntry ?: break
        if (entry.isDirectory) continue
        val name = flattenedName(entry.name) ?: continue
        destination.resolve(name).outputStream().use { input.copyTo(it) }
    }
}

private fun untarFlattened(input: InputStream, destination: File) {
    val reader = TarReader(input)
    while (true) {
        val entry = reader.next() ?: break
        if (!entry.isRegularFile) continue
        val name = flattenedName(entry.name) ?: continue
        destination.resolve(name).outputStream().use { reader.copyEntryTo(it) }
    }
}

/** Base name of an archive path, or null when it names no file of its own. */
private fun flattenedName(path: String): String? {
    val name = path.trimEnd('/').substringAfterLast('/')
    return name.takeUnless { it.isEmpty() || it == "." || it == ".." }
}

private class TarEntry(val name: String, val isRegularFile: Boolean)

/**
 * Reads ustar archives, including the PAX and GNU long-name records that GitHub tarballs and
 * Go's archive/tar write for paths over 100 bytes.
 */
private class TarReader(private val input: InputStream) {

    private val header = ByteArray(BLOCK_SIZE)
    private var remaining = 0L
    private var padding = 0L

    fun next(): TarEntry? {
        skipFully(remaining + padding)
        var longName: String? = null
        while (true) {
            if (!readHeader()) return null
            val size = parseNumber(SIZE_OFFSET, SIZE_LENGTH)
            remaining = size
            padding = (BLOCK_SIZE - size % BLOCK_SIZE) % BLOCK_SIZE
            when (val type = header[TYPE_OFFSET].toInt().toChar()) {
                TYPE_PAX -> {
                    longName = parsePaxPath(readEntry()) ?: longName
                }

                TYPE_GNU_LONG_NAME -> {
                    longName = readEntry().decodeToString().trimEnd('\u0000')
                }

                TYPE_PAX_GLOBAL, TYPE_GNU_LONG_LINK -> {
                    readEntry()
                }

                else -> {
                    val name = longName ?: headerName()
                    return TarEntry(name, type == TYPE_REGULAR || type == TYPE_REGULAR_OLD)
                }
            }
        }
    }

    fun copyEntryTo(output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw EOFException("truncated tar entry")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun readEntry(): ByteArray {
        if (remaining > MAX_METADATA_SIZE) throw IOException("tar metadata record too large")
        val content = input.readNBytes(remaining.toInt())
        if (content.size.toLong() != remaining) throw EOFException("truncated tar entry")
        remaining = 0
        skipFully(padding)
        padding = 0
        return content
    }

    /** False at the end-of-archive marker or the end of the stream. */
    private fun readHeader(): Boolean {
        val read = input.readNBytes(header, 0, BLOCK_SIZE)
        if (read == 0) return false
        if (read < BLOCK_SIZE) throw EOFException("truncated tar header")
        return header.any { it != 0.toByte() }
    }

    private fun headerName(): String {
        val name = field(NAME_OFFSET, NAME_LENGTH)
        val isUstar = field(MAGIC_OFFSET, MAGIC_LENGTH).startsWith("ustar")
        val prefix = if (isUstar) field(PREFIX_OFFSET, PREFIX_LENGTH) else ""
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    private fun field(offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && header[end] != 0.toByte()) end++
        return header.decodeToString(offset, end)
    }

    /** Octal, or base-256 when the high bit of the first byte is set (GNU, for sizes ≥ 8 GiB). */
    private fun parseNumber(offset: Int, length: Int): Long {
        if (header[offset].toInt() and 0x80 != 0) {
            var value = (header[offset].toLong() and 0x7f)
            for (index in offset + 1 until offset + length) {
                value = (value shl 8) or (header[index].toLong() and 0xff)
            }
            return value
        }
        val text = field(offset, length).trim()
        if (text.isEmpty()) return 0
        return text.toLongOrNull(8) ?: throw IOException("invalid tar number: $text")
    }

    private fun skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) {
                left -= skipped
                continue
            }
            if (input.read() < 0) throw EOFException("truncated tar archive")
            left--
        }
    }

    companion object {
        private const val BLOCK_SIZE = 512
        private const val NAME_OFFSET = 0
        private const val NAME_LENGTH = 100
        private const val SIZE_OFFSET = 124
        private const val SIZE_LENGTH = 12
        private const val TYPE_OFFSET = 156
        private const val MAGIC_OFFSET = 257
        private const val MAGIC_LENGTH = 6
        private const val PREFIX_OFFSET = 345
        private const val PREFIX_LENGTH = 155

        private const val TYPE_REGULAR = '0'
        private const val TYPE_REGULAR_OLD = '\u0000'
        private const val TYPE_PAX = 'x'
        private const val TYPE_PAX_GLOBAL = 'g'
        private const val TYPE_GNU_LONG_NAME = 'L'
        private const val TYPE_GNU_LONG_LINK = 'K'

        private const val MAX_METADATA_SIZE = 1L shl 20
        private const val PAX_PATH_KEY = "path"

        /** A PAX record is `<length> <key>=<value>\n`, the length counting the whole record. */
        private fun parsePaxPath(records: ByteArray): String? {
            var path: String? = null
            var offset = 0
            while (offset < records.size) {
                val space = records.indexOf(' '.code.toByte(), offset)
                if (space < 0) break
                val length = records.decodeToString(offset, space).toIntOrNull() ?: break
                val end = offset + length
                if (length <= 0 || end > records.size) break
                val record = records.decodeToString(space + 1, end - 1)
                val key = record.substringBefore('=')
                if (key == PAX_PATH_KEY) {
                    path = record.substringAfter('=')
                }
                offset = end
            }
            return path
        }

        private fun ByteArray.indexOf(byte: Byte, from: Int): Int {
            for (index in from until size) {
                if (this[index] == byte) return index
            }
            return -1
        }
    }
}
