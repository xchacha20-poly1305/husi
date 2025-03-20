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

import io.nekohasekai.sagernet.ktx.toStringPretty
import io.nekohasekai.sagernet.logLevelString
import libcore.Libcore
import org.json.JSONArray
import org.json.JSONObject

fun MieruBean.buildMieruConfig(port: Int, logLevel: Int): String {
    val serverInfo = JSONArray().apply {
        put(JSONObject().apply {
            put("ipAddress", serverAddress)
            put("portBindings", JSONArray().apply {
                put(JSONObject().apply {
                    put("port", serverPort)
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
                if (serverMuxNumber > 0) put("multiplexing", JSONObject().apply {
                    put("level", serverMuxNumber)
                })
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
    mtu = url.queryParameterNotBlank("mtu").toIntOrNull()
    serverMuxNumber = url.queryParameterNotBlank("multiplexing").toIntOrNull()?.takeIf {
        // Avoid invalid value
        it in 0..3
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
        addQueryParameter("multiplexing", it.toString())
    }
}.string