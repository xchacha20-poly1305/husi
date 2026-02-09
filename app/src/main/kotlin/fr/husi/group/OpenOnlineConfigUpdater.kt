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

package fr.husi.group

import fr.husi.R
import fr.husi.database.DataStore
import fr.husi.database.GroupManager
import fr.husi.database.ProxyGroup
import fr.husi.database.SubscriptionBean
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.shadowsocks.ShadowsocksBean
import fr.husi.fmt.shadowsocks.pluginToLocal
import fr.husi.ktx.Logs
import fr.husi.ktx.addPathSegments
import fr.husi.ktx.applyDefaultValues
import fr.husi.ktx.filterIsInstance
import fr.husi.ktx.generateUserAgent
import fr.husi.ktx.getIntOrNull
import fr.husi.ktx.getLongOrNull
import fr.husi.ktx.getStr
import fr.husi.libcore.Libcore
import fr.husi.libcore.URL
import fr.husi.repository.repo
import org.json.JSONObject

/** https://github.com/Shadowsocks-NET/OpenOnlineConfig */
object OpenOnlineConfigUpdater : GroupUpdater() {

    const val OOC_VERSION = 1
    val OOC_PROTOCOLS = listOf("shadowsocks")

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
            error(repo.getString(R.string.ooc_subscription_token_invalid))
        }

        val response = Libcore.newHttpClient().apply {
            if (DataStore.serviceState.started) {
                useSocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
            }
            // Strict !!!
            restrictedTLS()
            if (certSha256 != null) pinnedSHA256(certSha256)
        }.newRequest().apply {
            setURL(baseLink.string)
            setUserAgent(generateUserAgent(subscription.customUserAgent))
        }.execute()

        val oocResponse = JSONObject(response.contentString)

        val protocols = oocResponse.getJSONArray("protocols").filterIsInstance<String>()
        for (protocol in protocols) {
            if (protocol !in OOC_PROTOCOLS) {
                userInterface?.alert(
                    repo.getString(R.string.ooc_missing_protocol, protocol),
                )
            }
        }

        subscription.username = oocResponse.getStr("username").orEmpty()
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

                    bean.name = profile.getStr("name").orEmpty()
                    bean.serverAddress = profile.getStr("address").orEmpty()
                    bean.serverPort = profile.getIntOrNull("port") ?: 8388
                    bean.method = profile.getStr("method").orEmpty()
                    bean.password = profile.getStr("password").orEmpty()

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
