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

import fr.husi.ktx.queryParameterNotBlank
import fr.husi.ktx.toJsonStringKxs
import fr.husi.libcore.Libcore
import fr.husi.logLevelString

fun MieruBean.buildMieruConfig(port: Int, logLevel: Int): String {
    return mutableMapOf<String, Any?>(
        "activeProfile" to "default",
        "socks5Port" to port,
        "loggingLevel" to logLevel.takeIf { it > 0 }?.let { logLevelString(it).uppercase() },
        "advancedSettings" to mapOf("noCheckUpdate" to true),
        "profiles" to listOf(
            mutableMapOf<String, Any?>(
                "profileName" to "default",
                "user" to mapOf(
                    "name" to username,
                    "password" to password.also {
                        if (it.isEmpty()) error("mieru password is empty")
                    },
                ),
                "servers" to listOf(
                    mapOf(
                        "ipAddress" to finalAddress,
                        "portBindings" to listOf(
                            mapOf("port" to finalPort, "protocol" to protocol),
                        ),
                    ),
                ),
                "mtu" to mtu,
                "multiplexing" to mieruMuxToString(serverMuxNumber)?.let { mapOf("level" to it) },
                "handshakeMode" to "HANDSHAKE_NO_WAIT",
            ),
        ),
    ).toJsonStringKxs()
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