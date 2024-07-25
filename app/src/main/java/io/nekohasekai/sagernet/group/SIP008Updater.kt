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

import android.net.Uri
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.shadowsocks.parseShadowsocks
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.filterIsInstance
import io.nekohasekai.sagernet.ktx.generateUserAgent
import io.nekohasekai.sagernet.ktx.getLongOrNull
import libcore.Libcore
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
            val contentText = app.contentResolver.openInputStream(Uri.parse(subscription.link))
                ?.bufferedReader()
                ?.readText()

            sip008Response = contentText?.let { JSONObject(contentText) }
                ?: error(app.getString(R.string.no_proxies_found_in_subscription))
        } else {

            val response = Libcore.newHttpClient().apply {
                trySocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
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
