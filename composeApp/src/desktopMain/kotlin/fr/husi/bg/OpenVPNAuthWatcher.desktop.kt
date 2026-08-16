package fr.husi.bg

import fr.husi.resources.Res
import fr.husi.resources.openvpn_authentication
import fr.husi.vpn.firstVpnAuthPending
import kotlinx.coroutines.flow.map

internal object OpenVPNAuthWatcher {

    private val watcher = DesktopVpnAuthWatcher(
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

    fun start() = watcher.start()

    fun stop() = watcher.stop()
}
