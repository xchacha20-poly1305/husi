package fr.husi.bg

import fr.husi.ktx.Logs

actual object RouteAssetUpdater {
    actual suspend fun reconfigureUpdater() {
        runCatching {
            DesktopTaskScheduler.reconfigure(
                DesktopTaskRegistry.require("route-asset-auto-update"),
            )
        }.onFailure {
            Logs.e("reconfigure desktop route asset updater", it)
        }
    }
}
