package fr.husi.fmt.trusttunnel

import fr.husi.io.readQuicVarint
import fr.husi.io.writeQuicVarint
import fr.husi.ktx.splitAddress
import fr.husi.ktx.toPortOrNull
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import java.io.EOFException

/**
 * A TrustTunnel deep link: `tt://?` followed by unpadded URL-safe Base64 of TLV records, each a
 * QUIC variable-length tag, a QUIC variable-length length, then the value.
 *
 * https://github.com/TrustTunnel/TrustTunnel/blob/master/DEEP_LINK.md
 */
internal data class TrustTunnelLink(
    val hostname: String,
    /** Each `host:port`, an IPv6 host in brackets. */
    val addresses: List<String>,
    val customSni: String = "",
    val username: String,
    val password: String,
    val skipVerification: Boolean = false,
    /** Concatenated DER certificates, empty when the link pins none. */
    val certificate: ByteString = ByteString.EMPTY,
    val http3: Boolean = false,
    val name: String = "",
) {

    init {
        require(hostname.isNotEmpty()) { "missing hostname" }
        require(addresses.isNotEmpty()) { "missing addresses" }
        addresses.forEachIndexed { index, address ->
            require(isValidAddress(address)) { "address [$index] is invalid" }
        }
        require(username.isNotEmpty()) { "missing username" }
        require(password.isNotEmpty()) { "missing password" }
    }

    fun build(): String {
        val records = Buffer()
        records.writeRecord(TAG_VERSION, Buffer().also { it.writeQuicVarint(VERSION_WRITTEN) }.readByteArray())
        records.writeRecord(TAG_HOSTNAME, hostname)
        addresses.forEach { records.writeRecord(TAG_ADDRESSES, it) }
        if (customSni.isNotEmpty()) records.writeRecord(TAG_CUSTOM_SNI, customSni)
        records.writeRecord(TAG_USERNAME, username)
        records.writeRecord(TAG_PASSWORD, password)
        if (skipVerification) records.writeRecord(TAG_SKIP_VERIFICATION, byteArrayOf(1))
        if (certificate.size > 0) records.writeRecord(TAG_CERTIFICATE, certificate.toByteArray())
        if (http3) records.writeRecord(TAG_UPSTREAM_PROTOCOL, byteArrayOf(UPSTREAM_HTTP3))
        if (name.isNotEmpty()) records.writeRecord(TAG_NAME, name)
        val encoded = records.readByteString().base64Url().trimEnd('=')
        return "$SCHEME_PREFIX?$encoded"
    }

    companion object {
        private const val SCHEME_PREFIX = "tt://"

        private const val TAG_VERSION = 0x00L
        private const val TAG_HOSTNAME = 0x01L
        private const val TAG_ADDRESSES = 0x02L
        private const val TAG_CUSTOM_SNI = 0x03L
        private const val TAG_USERNAME = 0x05L
        private const val TAG_PASSWORD = 0x06L
        private const val TAG_SKIP_VERIFICATION = 0x07L
        private const val TAG_CERTIFICATE = 0x08L
        private const val TAG_UPSTREAM_PROTOCOL = 0x09L
        private const val TAG_NAME = 0x0CL
        private const val TAG_SUBSCRIPTION_URL = 0x0EL

        /**
         * Version 2 only adds the subscription URL, which these links never carry. Writing 1 keeps
         * them readable by clients that predate version 2.
         */
        private const val VERSION_WRITTEN = 1L
        private const val VERSION_MAX_SUPPORTED = 2L
        private const val SUBSCRIPTION_URL_PREFIX = "https://"
        private const val BOOL_FALSE: Byte = 0
        private const val BOOL_TRUE: Byte = 1
        private const val UPSTREAM_HTTP2: Byte = 1
        private const val UPSTREAM_HTTP3: Byte = 2

        fun parse(link: String): TrustTunnelLink {
            require(link.startsWith(SCHEME_PREFIX)) { "schema is not tt" }
            // The `?` arrived with draft 2; older links put the Base64 right after the scheme.
            val encoded = link.removePrefix(SCHEME_PREFIX).removePrefix("?")
            val records = encoded.decodeBase64()
                ?.let { Buffer().write(it) }
                ?: throw IllegalArgumentException("invalid base64")

            var hostname = ""
            val addresses = mutableListOf<String>()
            var customSni = ""
            var username = ""
            var password = ""
            var skipVerification = false
            var certificate = ByteString.EMPTY
            var http3 = false
            var name = ""
            var subscriptionUrl: String? = null
            try {
                while (!records.exhausted()) {
                    val tag = records.readQuicVarint()
                    val length = records.readQuicVarint()
                    require(length <= records.size) { "invalid length of tag $tag: $length" }
                    val value = records.readByteString(length)
                    when (tag) {
                        TAG_VERSION -> {
                            val version = Buffer().write(value).readQuicVarint()
                            require(version <= VERSION_MAX_SUPPORTED) { "unsupported version: $version" }
                        }

                        TAG_HOSTNAME -> hostname = value.strictUtf8(tag)
                        TAG_ADDRESSES -> {
                            val address = value.strictUtf8(tag)
                            require(isValidAddress(address)) { "invalid address: $address" }
                            addresses += address
                        }

                        TAG_CUSTOM_SNI -> customSni = value.strictUtf8(tag)
                        TAG_USERNAME -> username = value.strictUtf8(tag)
                        TAG_PASSWORD -> password = value.strictUtf8(tag)
                        TAG_SKIP_VERIFICATION -> skipVerification = value.bool(tag)
                        TAG_CERTIFICATE -> certificate = value
                        TAG_UPSTREAM_PROTOCOL -> {
                            http3 = when (val protocol = value.singleByte(tag)) {
                                UPSTREAM_HTTP2 -> false
                                UPSTREAM_HTTP3 -> true
                                else -> throw IllegalArgumentException("invalid upstream protocol: $protocol")
                            }
                        }

                        TAG_NAME -> name = value.strictUtf8(tag)
                        TAG_SUBSCRIPTION_URL -> {
                            val url = value.strictUtf8(tag)
                            require(url.startsWith(SUBSCRIPTION_URL_PREFIX)) { "subscription URL is not https: $url" }
                            subscriptionUrl = url
                        }

                        // Anti-DPI, IPv6 availability, client random prefix and DNS upstreams have
                        // no counterpart in a sing-box outbound.
                        else -> Unit
                    }
                }
            } catch (_: EOFException) {
                throw IllegalArgumentException("truncated record")
            }
            // A subscription URL makes the other fields optional; without them there is no
            // server to connect to.
            val hasServer = hostname.isNotEmpty() && addresses.isNotEmpty() &&
                username.isNotEmpty() && password.isNotEmpty()
            require(hasServer || subscriptionUrl == null) { "subscription-only links are not supported" }
            return TrustTunnelLink(
                hostname = hostname,
                addresses = addresses,
                customSni = customSni,
                username = username,
                password = password,
                skipVerification = skipVerification,
                certificate = certificate,
                http3 = http3,
                name = name,
            )
        }

        private fun isValidAddress(address: String): Boolean {
            return splitAddress(address)?.second?.toPortOrNull() != null
        }

        private fun ByteString.singleByte(tag: Long): Byte {
            require(size == 1) { "invalid length of tag $tag: $size" }
            return this[0]
        }

        private fun ByteString.bool(tag: Long): Boolean = when (val byte = singleByte(tag)) {
            BOOL_FALSE -> false
            BOOL_TRUE -> true
            else -> throw IllegalArgumentException("invalid boolean of tag $tag: $byte")
        }

        private fun ByteString.strictUtf8(tag: Long): String = try {
            toByteArray().decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            throw IllegalArgumentException("invalid UTF-8 in tag $tag")
        }

        private fun Buffer.writeRecord(tag: Long, value: String) {
            writeRecord(tag, value.encodeToByteArray())
        }

        private fun Buffer.writeRecord(tag: Long, value: ByteArray) {
            writeQuicVarint(tag)
            writeQuicVarint(value.size.toLong())
            write(value)
        }
    }
}
