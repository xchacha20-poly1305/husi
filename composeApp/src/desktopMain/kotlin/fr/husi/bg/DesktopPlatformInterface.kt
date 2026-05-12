package fr.husi.bg

import fr.husi.database.DataStore
import fr.husi.libcore.PlatformInterface
import fr.husi.repository.resolveDesktopRepository
import java.net.InetAddress

class DesktopPlatformInterface : PlatformInterface {

    override fun anchorSSID(): String {
        return DataStore.anchorSSID
    }

    override fun deviceName(): String? {
        return InetAddress.getLocalHost().getHostName()
    }

    override fun onGroupSelectedChange(
        group: String,
        old: String,
        now: String,
    ) {
        resolveDesktopRepository().serviceRuntime.trafficLooper?.updateSelectedTag(group, old, now)
    }

    override fun onDeepLink(deepLink: String) {
        DeepLinkDispatcher.emit(deepLink)
    }

    override fun onTask(taskId: String) {
        DesktopTaskRegistry.dispatch(taskId)
    }

}
