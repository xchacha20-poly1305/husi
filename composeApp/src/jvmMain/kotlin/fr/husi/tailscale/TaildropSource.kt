package fr.husi.tailscale

import fr.husi.ktx.asOkioSource
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import okio.Buffer
import okio.Source

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
    TaildropSource(file.name, file.size()) { file.asOkioSource() }
