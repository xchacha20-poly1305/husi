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

package io.nekohasekai.sagernet.fmt.mieru

import io.nekohasekai.sagernet.ktx.queryParameterNotBlank
import io.nekohasekai.sagernet.ktx.toStringPretty
import io.nekohasekai.sagernet.logLevelString
import libcore.Libcore
import org.json.JSONArray
import org.json.JSONObject

fun MieruBean.buildMieruConfig(port: Int, logLevel: Int): String {
    val serverInfo = JSONArray().apply {
        put(JSONObject().apply {
            put("ipAddress", finalAddress)
            put("portBindings", JSONArray().apply {
                put(JSONObject().apply {
                    put("port", finalPort)
                    put("protocol", protocol)
                })
            })
        })
    }
    return JSONObject().apply {
        put("activeProfile", "default")
        put("socks5Port", port)
        logLevel.takeIf { it > 0 }?.let {
            put("loggingLevel", logLevelString(it).uppercase())
        }
        put("advancedSettings", JSONObject().apply {
            put("noCheckUpdate", true) // v3.13.0
        })
        put("profiles", JSONArray().apply {
            put(JSONObject().apply {
                put("profileName", "default")
                put("user", JSONObject().apply {
                    put("name", username)
                    put("password", password.also {
                        if (it.isEmpty()) error("mieru password is empty")
                    })
                })
                put("servers", serverInfo)
                put("mtu", mtu)
                mieruMuxToString(serverMuxNumber)?.let { levelString ->
                    put("multiplexing", JSONObject().apply {
                        put("level", levelString)
                    })
                }
            })
        })
    }.toStringPretty()
}

// https://github.com/enfein/mieru/blob/b1cd50fabb2f893c7878388767d97370dbb7a660/pkg/appctl/url.go#L51
fun parseMieru(link: String): MieruBean = MieruBean().apply {
    val url = Libcore.parseURL(link)
    username = url.username
    password = url.password
    serverAddress = url.host
    serverPort = url.ports.toIntOrNull()

    name = url.queryParameterNotBlank("profile")
    mtu = url.queryParameterNotBlank("mtu")?.toIntOrNull()
    serverMuxNumber = url.queryParameter("multiplexing")?.let {
        parseMieruMux(it)
    }
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