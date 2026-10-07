package fr.husi.ktx

import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * V2Ray `pinnedPeerCertificateChainSha256`: certificate hashes chained as
 * `sha256(previous || sha256(next))`, encoded in standard Base64.
 * Returns an empty string when [pem] contains no block.
 */
fun pemToV2RayCertChainHash(pem: String): String {
    val chainHash = decodePemBlocks(pem)
        .map(ByteString::sha256)
        .reduceOrNull { previous, next ->
            Buffer().write(previous).write(next).sha256()
        }
        ?: return ""
    return chainHash.base64()
}

/**
 * Hysteria `pinSHA256`: hex SHA-256 of the first certificate's DER.
 * Returns an empty string when [pem] contains no block.
 */
fun pemToHysteriaCertSha256Hex(pem: String): String {
    val certificate = decodePemBlocks(pem).firstOrNull() ?: return ""
    return certificate.sha256().hex()
}

/**
 * sing-box `certificate_public_key_sha256`: Base64 SHA-256 of the first certificate's
 * SubjectPublicKeyInfo DER. Returns an empty string when [pem] contains no block.
 */
fun pemToSingPublicKeySha256(pem: String): String {
    val certificateDer = decodePemBlocks(pem).firstOrNull() ?: return ""
    val certificate = CertificateFactory.getInstance("X.509")
        .generateCertificate(certificateDer.toByteArray().inputStream()) as X509Certificate
    return certificate.publicKey.encoded.toByteString().sha256().base64()
}
