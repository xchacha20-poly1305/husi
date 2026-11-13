package fr.husi.bg

import fr.husi.resources.Res
import fr.husi.resources.openconnect_authentication
import fr.husi.vpn.firstVpnAuthPending
import kotlinx.coroutines.flow.map

internal object OpenConnectAuthWatcher {

    private val watcher = DesktopVpnAuthWatcher(
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

    fun start() = watcher.start()

    fun stop() = watcher.stop()
}
