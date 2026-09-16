/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package fr.husi.fmt.mieru

import fr.husi.ktx.blankAsNull
import fr.husi.ktx.isIpAddress
import fr.husi.ktx.queryParameterNotBlank
import fr.husi.ktx.kxs
import fr.husi.ktx.toJsonStringKxs
import fr.husi.libcore.Libcore
import fr.husi.logLevelString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun MieruBean.buildMieruConfig(port: Int, logLevel: Int): String {
    if (password.isEmpty()) error("mieru password is empty")
    val profile = buildJsonObject {
        put("profileName", "default")
        putJsonObject("user") {
            put("name", username)
            put("password", password)
        }
        putJsonArray("servers") {
            addJsonObject {
                putJsonArray("portBindings") {
                    addJsonObject {
                        put("port", finalPort)
                        put("protocol", protocol.uppercase())
                    }
                }
                // mieru refuses to parse a domain name in the ipAddress field.
                if (finalAddress.isIpAddress()) {
                    put("ipAddress", finalAddress)
                } else {
                    put("domainName", finalAddress)
                }
            }
        }
        put("mtu", mtu)
        mieruMuxToString(serverMuxNumber)?.let { level ->
            putJsonObject("multiplexing") { put("level", level) }
        }
        // "handshakeMode" to "HANDSHAKE_NO_WAIT",
        // https://github.com/enfein/mieru/issues/254
        // Mieru TCP mux long-time mutex holding + no wait = bug.
        put("handshakeMode", "HANDSHAKE_STANDARD")
        trafficPattern.blankAsNull()?.let { pattern ->
            put(
                "trafficPattern",
                runCatching {
                    pattern.parseMieruTrafficPattern()
                }.getOrElse { _ ->
                    Libcore.decodeMieruTrafficPattern(pattern).parseMieruTrafficPattern()
                },
            )
        }
    }
    return buildJsonObject {
        put("activeProfile", "default")
        put("socks5Port", port)
        logLevel.takeIf { it > 0 }?.let {
            put("loggingLevel", logLevelString(it).uppercase())
        }
        putJsonObject("advancedSettings") { put("noCheckUpdate", true) }
        putJsonArray("profiles") { add(profile) }
    }.toJsonStringKxs()
}

private fun String.parseMieruTrafficPattern(): JsonElement {
    val root = kxs.parseToJsonElement(this) as? JsonObject
        ?: error("mieru traffic pattern is not a JSON object")
    return root["trafficPattern"] ?: root
}

// https://github.com/enfein/mieru/blob/b1cd50fabb2f893c7878388767d97370dbb7a660/pkg/appctl/url.go#L51
fun parseMieru(link: String): MieruBean = MieruBean().apply {
    val url = Libcore.parseURL(link)
    username = url.username
    password = url.password
    serverAddress = url.host
    serverPort = url.ports.toIntOrNull() ?: defaultPort

    name = url.queryParameter("profile")
    mtu = url.queryParameterNotBlank("mtu")?.toIntOrNull() ?: 0
    serverMuxNumber = url.queryParameter("multiplexing")?.let {
        parseMieruMux(it)
    } ?: 0
    trafficPattern = url.queryParameter("traffic-pattern")
}

fun MieruBean.toUri(): String = Libcore.newURL("mierus").apply {
    username = this@toUri.username
    password = this@toUri.password
    host = serverAddress
    ports = serverPort.toString()

    name.takeIf { it.isNotBlank() }?.let {
        addQueryParameter("profile", it)
    }
    mtu.takeIf { it > 0 }?.let {
        addQueryParameter("mtu", it.toString())
    }
    serverMuxNumber.takeIf { it > 0 }?.let {
        addQueryParameter("multiplexing", mieruMuxToString(it))
    }
    trafficPattern.blankAsNull()?.let { trafficPattern ->
        val base64TrafficPattern = runCatching {
            Libcore.encodeMieruTrafficPattern(trafficPattern)
        }.getOrElse {
            trafficPattern
        }
        addQueryParameter("traffic-pattern", base64TrafficPattern)
    }
}.string

private fun parseMieruMux(link: String): Int? = when (link) {
    "MULTIPLEXING_OFF" -> 0
    "MULTIPLEXING_LOW" -> 1
    "MULTIPLEXING_MEDIUM" -> 2
    "MULTIPLEXING_HIGH" -> 3
    else -> null
}

private fun mieruMuxToString(level: Int): String? = when (level) {
    // 0 -> "MULTIPLEXING_OFF"
    1 -> "MULTIPLEXING_LOW"
    2 -> "MULTIPLEXING_MEDIUM"
    3 -> "MULTIPLEXING_HIGH"
    else -> null
}
