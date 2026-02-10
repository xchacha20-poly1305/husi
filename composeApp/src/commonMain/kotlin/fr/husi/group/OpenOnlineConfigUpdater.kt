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
import fr.husi.ktx.generateUserAgent
import fr.husi.ktx.kxs
import fr.husi.libcore.Libcore
import fr.husi.libcore.URL
import fr.husi.repository.repo
import kotlinx.serialization.Serializable
import fr.husi.resources.*

/** https://github.com/Shadowsocks-NET/OpenOnlineConfig */
object OpenOnlineConfigUpdater : GroupUpdater() {

    const val OOC_VERSION = 1
    val OOC_PROTOCOLS = listOf("shadowsocks")

    @Serializable
    private data class OOCSubscriptionToken(
        val version: Int,
        val baseUrl: String,
        val secret: String,
        val userId: String,
        val certSha256: String? = null,
    )

    @Serializable
    private data class OOCResponse(
        val protocols: List<String> = emptyList(),
        val username: String? = null,
        val bytesUsed: Long? = null,
        val bytesRemaining: Long? = null,
        val expiryDate: Long? = null,
        val shadowsocks: List<OOCShadowsocksProfile> = emptyList(),
    )

    @Serializable
    private data class OOCShadowsocksProfile(
        val name: String? = null,
        val address: String? = null,
        val port: Int? = null,
        val method: String? = null,
        val password: String? = null,
        val pluginName: String? = null,
        val pluginOptions: String? = null,
    )

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean,
    ) {
        val token: OOCSubscriptionToken
        val baseLink: URL
        val certSha256: String?
        try {
            token = kxs.decodeFromString(subscription.token)
            val version = token.version
            if (version != OOC_VERSION) {
                error("Unsupported OOC version $version")
            }
            val baseUrl = token.baseUrl
            when {
                baseUrl.isBlank() -> {
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
            val secret = token.secret
            if (secret.isBlank()) error("Missing field: secret")
            baseLink.addPathSegments(secret, "ooc/v1")

            val userId = token.userId
            if (userId.isBlank()) error("Missing field: userId")
            baseLink.addPathSegments(userId)
            certSha256 = token.certSha256
        } catch (e: Exception) {
            Logs.e("OOC token check failed, token = ${subscription.token}", e)
            error(repo.getString(Res.string.ooc_subscription_token_invalid))
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

        val oocResponse: OOCResponse = try {
            kxs.decodeFromString(response.contentString)
        } catch (e: Exception) {
            Logs.e("OOC response parse failed", e)
            error(repo.getString(Res.string.ooc_subscription_token_invalid))
        }

        val protocols = oocResponse.protocols
        for (protocol in protocols) {
            if (protocol !in OOC_PROTOCOLS) {
                userInterface?.alert(
                    repo.getString(Res.string.ooc_missing_protocol, protocol),
                )
            }
        }

        subscription.username = oocResponse.username.orEmpty()
        subscription.bytesUsed = oocResponse.bytesUsed ?: -1
        subscription.bytesRemaining = oocResponse.bytesRemaining ?: -1
        subscription.expiryDate = oocResponse.expiryDate ?: -1
        subscription.applyDefaultValues()

        val proxies = mutableListOf<AbstractBean>()

        for (protocol in protocols) {
            when (protocol) {
                "shadowsocks" -> for (profile in oocResponse.shadowsocks) {
                    val bean = ShadowsocksBean()

                    bean.name = profile.name.orEmpty()
                    bean.serverAddress = profile.address.orEmpty()
                    bean.serverPort = profile.port ?: 8388
                    bean.method = profile.method.orEmpty()
                    bean.password = profile.password.orEmpty()

                    // check plugin exists?
                    // check pluginVersion?
                    // TODO support pluginArguments
                    val pluginName = profile.pluginName
                    if (!pluginName.isNullOrBlank()) {
                        bean.plugin = pluginName + ";" + profile.pluginOptions.orEmpty()
                    }

                    proxies.add(bean.applyDefaultValues().apply { pluginToLocal() })
                }
            }
        }

        tidyProxies(proxies, subscription, proxyGroup, userInterface, byUser)
    }
}
