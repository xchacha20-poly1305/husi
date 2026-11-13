package fr.husi.bg

import android.content.Context
import fr.husi.resources.Res
import fr.husi.resources.openconnect_authentication
import fr.husi.vpn.firstVpnAuthPending
import kotlinx.coroutines.flow.map

object OpenConnectAuthWatcher {

    private val watcher = VpnAuthNotificationWatcher(
        notificationId = 3,
        channelId = "service-openconnect-auth",
        title = Res.string.openconnect_authentication,
        logLabel = "openconnect auth watcher",
        pending = {
            subscribeOpenConnectStatus().map { update ->
                firstVpnAuthPending(
                    endpoints = update.endpoints,
                    state = { it.state },
                    challengeId = { status ->
                        status.authChallenge?.id
                    },
                    tag = { it.endpointTag },
                )
            }
        },
    )

    fun start(context: Context) = watcher.start(context)

    fun stop(context: Context) = watcher.stop(context)
}
