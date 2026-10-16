package fr.husi.tailscale

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import kotlinx.io.Buffer as KotlinxBuffer
import kotlinx.io.RawSource
import kotlinx.io.readByteArray
import okio.Buffer
import okio.Source
import okio.Timeout

const val TAILDROP_UNKNOWN_SIZE = -1L

class TaildropSource(
    val name: String,
    val size: Long,
    private val openSource: () -> Source,
) {
    fun open(): Source = openSource()
}

fun TaildropSource(name: String, text: String): TaildropSource {
    val bytes = text.encodeToByteArray()
    return TaildropSource(name, bytes.size.toLong()) { Buffer().write(bytes) }
}

fun TaildropSource(file: PlatformFile): TaildropSource =
    TaildropSource(file.name, file.size()) { file.source().asOkioSource() }

private fun RawSource.asOkioSource(): Source = object : Source {
    override fun read(sink: Buffer, byteCount: Long): Long {
        if (byteCount == 0L) return 0L
        val scratch = KotlinxBuffer()
        val read = readAtMostTo(scratch, byteCount)
        if (read <= 0L) return read
        sink.write(scratch.readByteArray())
        return read
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() {
        this@asOkioSource.close()
    }
}
