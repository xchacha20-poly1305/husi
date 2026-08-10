package fr.husi.bg

import fr.husi.libcore.PlatformInterface
import fr.husi.repository.resolveDesktopRepository

class DesktopPlatformInterface : PlatformInterface {

    override fun onGroupSelectedChange(
        group: String,
        old: String,
        now: String,
    ) {
        resolveDesktopRepository().serviceRuntime.trafficLooper?.updateSelectedTag(group, old, now)
    }

    override fun onTask(taskId: String) {
        DesktopTaskRegistry.dispatch(taskId)
    }

}
