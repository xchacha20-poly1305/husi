package fr.husi.bg

import fr.husi.ktx.Logs

actual object SubscriptionUpdater {
    actual suspend fun reconfigureUpdater() {
        runCatching {
            DesktopTaskScheduler.reconfigure(
                DesktopTaskRegistry.require("subscription-auto-update"),
            )
        }.onFailure {
            Logs.e("reconfigure desktop subscription updater", it)
        }
    }
}
