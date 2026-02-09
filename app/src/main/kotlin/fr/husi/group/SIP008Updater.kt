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

import androidx.core.net.toUri
import fr.husi.R
import fr.husi.database.DataStore
import fr.husi.database.GroupManager
import fr.husi.database.ProxyGroup
import fr.husi.database.SubscriptionBean
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.shadowsocks.parseShadowsocks
import fr.husi.ktx.Logs
import fr.husi.ktx.applyDefaultValues
import fr.husi.ktx.filterIsInstance
import fr.husi.ktx.generateUserAgent
import fr.husi.ktx.getLongOrNull
import fr.husi.libcore.Libcore
import fr.husi.repository.repo
import org.json.JSONObject

/** https://shadowsocks.org/doc/sip008.html */
object SIP008Updater : GroupUpdater() {

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean,
    ) {
        if (subscription.link.startsWith("http://")) Logs.w("Use SIP008 with HTTP!")

        val sip008Response: JSONObject
        if (subscription.link.startsWith("content://")) {
            val contentText =
                repo.context.contentResolver.openInputStream(subscription.link.toUri())
                    ?.bufferedReader()
                    ?.readText()

            sip008Response = contentText?.let { JSONObject(contentText) }
                ?: error(repo.getString(R.string.no_proxies_found_in_subscription))
        } else {

            val response = Libcore.newHttpClient().apply {
                if (DataStore.serviceState.started) {
                    useSocks5(
                        DataStore.mixedPort,
                        DataStore.inboundUsername,
                        DataStore.inboundPassword,
                    )
                }
                // Strict !!!
                restrictedTLS()
            }.newRequest().apply {
                setURL(subscription.link)
                setUserAgent(generateUserAgent(subscription.customUserAgent))
            }.execute()

            sip008Response = JSONObject(response.contentString)
        }

        subscription.bytesUsed = sip008Response.getLongOrNull("bytes_used") ?: -1
        subscription.bytesRemaining = sip008Response.getLongOrNull("bytes_remaining") ?: -1
        subscription.applyDefaultValues()

        val servers = sip008Response.getJSONArray("servers").filterIsInstance<JSONObject>()

        val proxies = mutableListOf<AbstractBean>()
        for (profile in servers) {
            val bean = profile.parseShadowsocks()
            proxies.add(bean.applyDefaultValues())
        }

        tidyProxies(proxies, subscription, proxyGroup, userInterface, byUser)
    }
}
