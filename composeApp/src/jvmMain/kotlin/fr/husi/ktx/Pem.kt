package fr.husi.ktx

import okio.ByteString
import okio.ByteString.Companion.decodeBase64

private const val PEM_BEGIN_PREFIX = "-----BEGIN "
private const val PEM_END_PREFIX = "-----END "

/** Decodes the DER bodies of every PEM block in [pem], in order. Blocks with invalid Base64 are skipped. */
fun decodePemBlocks(pem: String): List<ByteString> {
    val blocks = mutableListOf<ByteString>()
    var body: StringBuilder? = null
    for (line in pem.lineSequence().map(String::trim)) {
        when {
            line.startsWith(PEM_BEGIN_PREFIX) -> body = StringBuilder()

            line.startsWith(PEM_END_PREFIX) -> {
                body?.toString()?.decodeBase64()?.let { blocks += it }
                body = null
            }

            else -> body?.append(line)
        }
    }
    return blocks
}

private const val PEM_LINE_LENGTH = 64

/** Encodes [der] as one PEM block of [type], e.g. `CERTIFICATE`. */
fun encodePemBlock(type: String, der: ByteString): String = buildString {
    append(PEM_BEGIN_PREFIX).append(type).append("-----\n")
    der.base64().chunked(PEM_LINE_LENGTH).forEach { append(it).append('\n') }
    append(PEM_END_PREFIX).append(type).append("-----\n")
}
