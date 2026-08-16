package fr.husi.bg

import android.content.Context
import fr.husi.resources.Res
import fr.husi.resources.openvpn_authentication
import fr.husi.vpn.firstVpnAuthPending
import kotlinx.coroutines.flow.map

object OpenVPNAuthWatcher {

    private val watcher = VpnAuthNotificationWatcher(
        notificationId = 4,
        channelId = "service-openvpn-auth",
        title = Res.string.openvpn_authentication,
        logLabel = "openvpn auth watcher",
        pending = {
            subscribeOpenVPNStatus().map { update ->
                firstVpnAuthPending(
                    endpoints = update.endpointsList,
                    state = { it.state },
                    challengeId = { status ->
                        status.challenge.takeIf { status.hasChallenge() }?.id
                    },
                    tag = { it.endpointTag },
                )
            }
        },
    )

    fun start(context: Context) = watcher.start(context)

    fun stop(context: Context) = watcher.stop(context)
}
