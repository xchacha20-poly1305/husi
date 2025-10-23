package io.nekohasekai.sagernet.ktx

import android.util.Base64
import com.google.gson.JsonParser
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.Serializable
import io.nekohasekai.sagernet.fmt.anytls.parseAnyTLS
import io.nekohasekai.sagernet.fmt.http.parseHttp
import io.nekohasekai.sagernet.fmt.hysteria.parseHysteria1
import io.nekohasekai.sagernet.fmt.hysteria.parseHysteria2
import io.nekohasekai.sagernet.fmt.juicity.parseJuicity
import io.nekohasekai.sagernet.fmt.mieru.parseMieru
import io.nekohasekai.sagernet.fmt.naive.parseNaive
import io.nekohasekai.sagernet.fmt.parseUniversal
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.fmt.socks.parseSOCKS
import io.nekohasekai.sagernet.fmt.trojan.parseTrojan
import io.nekohasekai.sagernet.fmt.tuic.parseTuic
import io.nekohasekai.sagernet.fmt.v2ray.parseV2Ray
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.io.use

// JSON & Base64

fun JSONObject.toStringPretty(): String {
    return gson.toJson(JsonParser.parseString(this.toString()))
}

inline fun <reified T : Any> JSONArray.filterIsInstance(): List<T> {
    val list = mutableListOf<T>()
    for (i in 0 until this.length()) {
        if (this[i] is T) list.add(this[i] as T)
    }
    return list
}

inline fun JSONArray.forEach(block: (Any) -> Unit) {
    for (i in 0 until length()) {
        block(this[i])
    }
}

inline fun JSONObject.forEach(action: (key: String, value: Any) -> Unit) {
    for (k in this.keys()) {
        action(k, this.get(k))
    }
}

fun isJsonObjectValid(j: Any): Boolean {
    if (j is JSONObject) return true
    if (j is JSONArray) return true
    try {
        JSONObject(j as String)
    } catch (ex: JSONException) {
        try {
            JSONArray(j)
        } catch (ex1: JSONException) {
            return false
        }
    }
    return true
}

// Be careful with null value with String.
// https://stackoverflow.com/questions/18226288/json-jsonobject-optstring-returns-string-null

fun JSONObject.getStr(name: String): String? {
    val obj = this.opt(name) ?: return null
    if (obj is String) {
        return obj.blankAsNull()
    } else {
        return null
    }
}

fun JSONObject.getBool(name: String): Boolean? {
    return try {
        getBoolean(name)
    } catch (_: Exception) {
        null
    }
}


fun JSONObject.getIntOrNull(name: String): Int? {
    return try {
        getInt(name)
    } catch (_: Exception) {
        null
    }
}

fun JSONObject.getLongOrNull(name: String): Long? {
    return try {
        getLong(name)
    } catch (_: Exception) {
        null
    }
}

fun JSONObject.getDoubleOrNull(name: String): Double? {
    return try {
        getDouble(name)
    } catch (_: Exception) {
        null
    }
}

fun JSONObject.getObject(key: String): JSONObject? {
    return try {
        getJSONObject(key)
    } catch (_: Exception) {
        null
    }
}

fun JSONObject.getArray(key: String): JSONArray? {
    return try {
        getJSONArray(key)
    } catch (_: Exception) {
        null
    }
}

fun String.b64EncodeUrlSafe(): String {
    return toByteArray().b64EncodeUrlSafe()
}

fun ByteArray.b64EncodeUrlSafe(): String {
    return String(Base64.encode(this, Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE))
}

// v2rayN Style
fun ByteArray.b64EncodeOneLine(): String {
    return String(Base64.encode(this, Base64.NO_WRAP))
}

fun ByteArray.b64EncodeDefault(): String {
    return String(Base64.encode(this, Base64.DEFAULT))
}

fun String.b64Decode(): ByteArray {
    var ret: ByteArray? = null

    // padding 自动处理，不用理
    // URLSafe 需要替换这两个，不要用 URL_SAFE 否则处理非 Safe 的时候会乱码
    val str = replace("-", "+").replace("_", "/")

    val flags = listOf(
        Base64.DEFAULT, // 多行
        Base64.NO_WRAP, // 单行
    )

    for (flag in flags) {
        try {
            ret = Base64.decode(str, flag)
        } catch (_: Exception) {
        }
        if (ret != null) return ret
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