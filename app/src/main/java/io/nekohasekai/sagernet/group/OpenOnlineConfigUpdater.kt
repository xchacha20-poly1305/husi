/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
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

package io.nekohasekai.sagernet.group

import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.shadowsocks.ShadowsocksBean
import io.nekohasekai.sagernet.fmt.shadowsocks.pluginToLocal
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.addPathSegments
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.filterIsInstance
import io.nekohasekai.sagernet.ktx.getIntOrNull
import io.nekohasekai.sagernet.ktx.getLongOrNull
import io.nekohasekai.sagernet.ktx.getStr
import io.nekohasekai.sagernet.ktx.socksInfo
import libcore.Libcore
import libcore.URL
import org.json.JSONObject

const val OOC_VERSION = 1
val OOC_PROTOCOLS = listOf("shadowsocks")

/** https://github.com/Shadowsocks-NET/OpenOnlineConfig */
object OpenOnlineConfigUpdater : GroupUpdater() {

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean,
    ) {
        val apiToken: JSONObject
        val baseLink: URL
        val certSha256: String?
        try {
            apiToken = JSONObject(subscription.token)

            val version = apiToken.getIntOrNull("version")
            if (version != OOC_VERSION) {
                if (version != null) {
                    error("Unsupported OOC version $version")
                } else {
                    error("Missing field: version")
                }
            }
            val baseUrl = apiToken.getStr("baseUrl")
            when {
                baseUrl.isNullOrBlank() -> {
                    error("Missing field: baseUrl")
                }

                baseUrl.endsWith("/") -> {
                    error("baseUrl must not contain a trailing slash")
                }

                !baseUrl.startsWith("https://") -> {
                    error("Protocol scheme must be https")
                }

                else -> baseLink = Libcore.parseURL(baseUrl)
            }
            val secret = apiToken.getStr("secret")
            if (secret.isNullOrBlank()) error("Missing field: secret")
            baseLink.addPathSegments(secret, "ooc/v1")

            val userId = apiToken.getStr("userId")
            if (userId.isNullOrBlank()) error("Missing field: userId")
            baseLink.addPathSegments(userId)
            certSha256 = apiToken.getStr("certSha256")
        } catch (e: Exception) {
            Logs.e("OOC token check failed, token = ${subscription.token}", e)
            error(app.getString(R.string.ooc_subscription_token_invalid))
        }

        val response = Libcore.newHttpClient().apply {
            trySocks5(socksInfo())
            // Strict !!!
            restrictedTLS()
            if (certSha256 != null) pinnedSHA256(certSha256)
        }.newRequest().apply {
            setURL(subscription.link)
            setUserAgent(subscription.customUserAgent.takeIf { it.isNotBlank() } ?: USER_AGENT)
        }.execute()

        val oocResponse = JSONObject(response.contentString)

        val protocols = oocResponse.getJSONArray("protocols").filterIsInstance<String>()
        for (protocol in protocols) {
            if (protocol !in OOC_PROTOCOLS) {
                userInterface?.alert(app.getString(R.string.ooc_missing_protocol, protocol))
            }
        }

        subscription.username = oocResponse.getStr("username")
        subscription.bytesUsed = oocResponse.getLongOrNull("bytesUsed") ?: -1
        subscription.bytesRemaining = oocResponse.getLongOrNull("bytesRemaining") ?: -1
        subscription.expiryDate = oocResponse.getLongOrNull("expiryDate") ?: -1
        subscription.applyDefaultValues()

        val proxies = mutableListOf<AbstractBean>()

        for (protocol in protocols) {
            val profilesInProtocol =
                oocResponse.getJSONArray(protocol).filterIsInstance<JSONObject>()

            when (protocol) {
                "shadowsocks" -> for (profile in profilesInProtocol) {
                    val bean = ShadowsocksBean()

                    bean.name = profile.getStr("name")
                    bean.serverAddress = profile.getStr("address")
                    bean.serverPort = profile.getInt("port")
                    bean.method = profile.getStr("method")
                    bean.password = profile.getStr("password")

                    // check plugin exists?
                    // check pluginVersion?
                    // TODO support pluginArguments
                    val pluginName = profile.getStr("pluginName")
                    if (!pluginName.isNullOrBlank()) {
                        bean.plugin = pluginName + ";" + profile.getStr("pluginOptions")
                    }

                    proxies.add(bean.applyDefaultValues().apply { pluginToLocal() })
                }
            }
        }

        tidyProxies(proxies, subscription, proxyGroup, userInterface, byUser)
    }
}