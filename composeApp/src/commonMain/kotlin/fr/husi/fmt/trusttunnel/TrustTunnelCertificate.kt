package fr.husi.fmt.trusttunnel

import fr.husi.ktx.decodePemBlocks
import fr.husi.ktx.encodePemBlock
import okio.Buffer
import okio.ByteString

// A deep link carries a certificate chain as its DER certificates concatenated, with nothing
// between them; only each certificate's own ASN.1 header tells where it ends.

private const val PEM_TYPE_CERTIFICATE = "CERTIFICATE"

private const val ASN1_SEQUENCE: Byte = 0x30
private const val ASN1_LONG_FORM_FLAG = 0x80
private const val ASN1_LONG_FORM_LENGTH_SIZE_MASK = 0x7F

/** Keeps the length within an Int, far beyond any certificate. */
private const val ASN1_MAX_LENGTH_SIZE = 3

/** Converts the concatenated DER certificates of a link into one PEM block per certificate. */
internal fun certificateChainToPem(chain: ByteString): String {
    return splitDerCertificates(chain).joinToString("") { encodePemBlock(PEM_TYPE_CERTIFICATE, it) }
}

/** Converts every PEM block in [pem] into the concatenated DER form a link carries. */
internal fun pemToCertificateChain(pem: String): ByteString {
    val certificates = decodePemBlocks(pem)
    require(certificates.isNotEmpty()) { "no PEM block in certificate" }
    val chain = Buffer()
    certificates.forEach { chain.write(it) }
    return chain.readByteString()
}

internal fun splitDerCertificates(chain: ByteString): List<ByteString> {
    val certificates = mutableListOf<ByteString>()
    var start = 0
    while (start < chain.size) {
        require(chain[start] == ASN1_SEQUENCE) { "no ASN.1 SEQUENCE at offset $start" }
        var cursor = start + 1
        require(cursor < chain.size) { "truncated DER certificate" }
        val lengthByte = chain[cursor++].toInt() and 0xFF
        val bodyLength = if (lengthByte and ASN1_LONG_FORM_FLAG == 0) {
            lengthByte
        } else {
            val lengthSize = lengthByte and ASN1_LONG_FORM_LENGTH_SIZE_MASK
            require(lengthSize in 1..ASN1_MAX_LENGTH_SIZE && cursor + lengthSize <= chain.size) {
                "invalid ASN.1 length at offset $start"
            }
            var length = 0
            repeat(lengthSize) { length = (length shl 8) or (chain[cursor++].toInt() and 0xFF) }
            length
        }
        val end = cursor + bodyLength
        require(end <= chain.size) { "truncated DER certificate" }
        certificates += chain.substring(start, end)
        start = end
    }
    return certificates
}
