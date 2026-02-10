package fr.husi.ktx

import fr.husi.fmt.AbstractBean
import fr.husi.fmt.Serializable
import fr.husi.fmt.anytls.parseAnyTLS
import fr.husi.fmt.http.parseHttp
import fr.husi.fmt.hysteria.parseHysteria1
import fr.husi.fmt.hysteria.parseHysteria2
import fr.husi.fmt.juicity.parseJuicity
import fr.husi.fmt.mieru.parseMieru
import fr.husi.fmt.naive.parseNaive
import fr.husi.fmt.parseUniversal
import fr.husi.fmt.shadowsocks.parseShadowsocks
import fr.husi.fmt.socks.parseSOCKS
import fr.husi.fmt.trojan.parseTrojan
import fr.husi.fmt.tuic.parseTuic
import fr.husi.fmt.v2ray.parseV2Ray
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

fun String.b64EncodeUrlSafe(): String {
    return toByteArray().b64EncodeUrlSafe()
}

fun ByteArray.b64EncodeUrlSafe(): String {
    return kotlin.io.encoding.Base64.UrlSafe.encode(this)
}

// v2rayN Style
fun ByteArray.b64EncodeOneLine(): String {
    return kotlin.io.encoding.Base64.Default.encode(this)
}

fun String.b64EncodeOneLine(): String {
    return toByteArray().b64EncodeOneLine()
}

fun String.b64Decode(): ByteArray {
    // padding 自动处理，不用理
    // URLSafe 需要替换这两个，不要用 UrlSafe 否则处理非 Safe 的时候会乱码
    val str = replace("-", "+").replace("_", "/")

    val decoders = listOf(
        kotlin.io.encoding.Base64.Default,
        kotlin.io.encoding.Base64.Mime,
    )

    for (decoder in decoders) {
        try {
            return decoder.decode(str)
        } catch (_: Exception) {
        }
    }

    throw IllegalStateException("Cannot decode base64")
}

fun String.b64DecodeToString(): String {
    return b64Decode().decodeToString()
}

// zlib

fun ByteArray.zlibCompress(level: Int): ByteArray {
    // Compress the bytes
    // 1 to 4 bytes/char for UTF-8
    val output = ByteArray(size * 4)
    val compressor = Deflater(level).apply {
        setInput(this@zlibCompress)
        finish()
    }
    val compressedDataLength: Int = compressor.deflate(output)
    compressor.end()
    return output.copyOfRange(0, compressedDataLength)
}

fun ByteArray.zlibDecompress(): ByteArray {
    val inflater = Inflater()
    val outputStream = ByteArrayOutputStream()

    return outputStream.use {
        val buffer = ByteArray(1024)

        inflater.setInput(this)

        var count = -1
        while (count != 0) {
            count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }

        inflater.end()
        outputStream.toByteArray()
    }
}

// Sub

class SubscriptionFoundException(val link: String) : RuntimeException()

suspend fun parseProxies(text: String): List<AbstractBean> {
    val links = text.split('\n').flatMap { it.trim().split(' ') }
    val linksByLine = text.split('\n').map { it.trim() }

    val entities = ArrayList<AbstractBean>()
    val entitiesByLine = ArrayList<AbstractBean>()

    suspend fun String.parseLink(entities: MutableList<AbstractBean>) {
        if (startsWith("sing-box://import-remote-profile?") || startsWith("husi://subscription?")) {
            throw SubscriptionFoundException(this)
        }

        val scheme = this.substringBefore("://")
        when (scheme) {
            "husi" -> {
                Logs.d("Try parse universal link: $this")
                runCatching {
                    entities.add(parseUniversal(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

            "socks", "socks4", "socks4a", "socks5" -> {
                Logs.d("Try parse socks link: $this")
                runCatching {
                    entities.add(parseSOCKS(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

            "http", "https" -> {
                Logs.d("Try parse http link: $this")
                try {
                    entities.add(parseHttp(this))
                } catch (e: Exception) {
                    Logs.w(e)
                }
            }

            "vmess", "vless" -> {
                Logs.d("Try parse v2ray link: $this")
                runCatching {
                    entities.add(parseV2Ray(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

            // Trojan-go was partially compatible
            "trojan", "trojan-go" -> {
                Logs.d("Try parse trojan link: $this")
                runCatching {
                    entities.add(parseTrojan(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

            "ss" -> {
                Logs.d("Try parse shadowsocks link: $this")
                runCatching {
                    entities.add(parseShadowsocks(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

            "naive+https", "naive+quic" -> {
                Logs.d("Try parse naive link: $this")
                runCatching {
                    entities.add(parseNaive(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

            "hysteria" -> {
                Logs.d("Try parse hysteria1 link: $this")
                runCatching {
                    entities.add(parseHysteria1(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

            "hysteria2", "hy2" -> {
                Logs.d("Try parse hysteria2 link: $this")
                runCatching {
                    entities.add(parseHysteria2(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

            "tuic" -> {
                Logs.d("Try parse TUIC link: $this")
                runCatching {
                    entities.add(parseTuic(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

            "juicity" -> {
                Logs.d("Try parse Juicity link: $this")
                runCatching {
                    entities.add(parseJuicity(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

            "mierus" -> {
                Logs.d("Try parse Mieru link: $this")
                runCatching {
                    entities.add(parseMieru(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

            "anytls" -> {
                Logs.d("Try parse AnyTLS link: $this")
                runCatching {
                    entities.add(parseAnyTLS(this))
                }.onFailure {
                    Logs.w(it)
                }
            }

        }
    }

    for (link in links) {
        link.parseLink(entities)
    }
    for (link in linksByLine) {
        link.parseLink(entitiesByLine)
    }
    var isBadLink = false
    if (entities.onEach { it.initializeDefaultValues() }.size == entitiesByLine.onEach { it.initializeDefaultValues() }.size) run test@{
        entities.forEachIndexed { index, bean ->
            val lineBean = entitiesByLine[index]
            if (bean == lineBean && bean.displayName() != lineBean.displayName()) {
                isBadLink = true
                return@test
            }
        }
    }
    return if (entities.size > entitiesByLine.size) entities else entitiesByLine
}

fun <T : Serializable> T.applyDefaultValues(): T {
    initializeDefaultValues()
    return this
}

fun String.substringBetween(after: String, before: String): String {
    return substringAfter(after).substringBefore(before)
}

fun formatTime(millis: Long): String {
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
}
