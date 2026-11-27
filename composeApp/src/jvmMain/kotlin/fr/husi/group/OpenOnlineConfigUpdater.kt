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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package fr.husi.group

import fr.husi.database.ProxyGroup
import fr.husi.database.SubscriptionBean
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.shadowsocks.ShadowsocksBean
import fr.husi.fmt.shadowsocks.pluginToLocal
import fr.husi.ktx.Logs
import fr.husi.ktx.applyDefaultValues
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.kxs
import fr.husi.net.HTTP_TIMEOUT
import fr.husi.net.HttpRequestOptions
import fr.husi.net.basicAuthorization
import fr.husi.net.buildUserAgent
import fr.husi.net.localHttpProxy
import fr.husi.net.requireOkBody
import fr.husi.net.withHttpDeadline
import fr.husi.net.withoutCredentials
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.ooc_missing_protocol
import fr.husi.resources.ooc_subscription_token_invalid
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

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

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        byUser: Boolean,
        warnings: MutableList<GroupUpdateWarning>,
    ): GroupUpdateResult.Success {
        val repository = resolveRepository()
        val token: OOCSubscriptionToken
        val baseLink: HttpUrl
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
            }
            val secret = token.secret
            if (secret.isBlank()) error("Missing field: secret")

            val userId = token.userId
            if (userId.isBlank()) error("Missing field: userId")
            baseLink = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment(secret)
                .addPathSegments("ooc/v1")
                .addPathSegment(userId)
                .build()
            certSha256 = token.certSha256?.blankAsNull()
        } catch (e: Exception) {
            Logs.e("OOC token check failed, token = ${subscription.token}", e)
            error(repository.getString(Res.string.ooc_subscription_token_invalid))
        }

        val authorization = baseLink.basicAuthorization()
        val targetUrl = baseLink.withoutCredentials()
        val options = HttpRequestOptions(
            proxy = localHttpProxy(),
            // Strict !!!
            restrictedTls = true,
            pinnedSha256 = certSha256,
        )
        val oocResponse: OOCResponse = try {
            withHttpDeadline(HTTP_TIMEOUT) {
                resolveOpenOnlineConfigApi().fetch(
                    url = targetUrl,
                    userAgent = buildUserAgent(subscription.customUserAgent),
                    authorization = authorization,
                    options = options,
                )
            }.requireOkBody()
        } catch (e: SerializationException) {
            Logs.e("OOC response parse failed", e)
            error(repository.getString(Res.string.ooc_subscription_token_invalid))
        } catch (e: IllegalArgumentException) {
            Logs.e("OOC response parse failed", e)
            error(repository.getString(Res.string.ooc_subscription_token_invalid))
        }

        val protocols = oocResponse.protocols
        for (protocol in protocols) {
            if (protocol !in OOC_PROTOCOLS) {
                warnings += GroupUpdateWarning(
                    proxyGroup.displayName(),
                    repository.getString(Res.string.ooc_missing_protocol, protocol),
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

        return tidyProxies(proxies, subscription, proxyGroup, byUser, warnings)
    }
}
