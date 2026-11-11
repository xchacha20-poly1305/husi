package fr.husi.fmt

import fr.husi.database.DataStore
import fr.husi.ktx.blankAsNull
import fr.husi.platform.PlatformInfo

internal actual suspend fun SingBoxOptions.Inbound_TunOptions.applyPlatformConfig() {
    val networkManager = DataStore.networkManagerTun.get()
    auto_route = !networkManager
    interface_name = DataStore.tunInterfaceName.get().blankAsNull()
    if (!networkManager && DataStore.tunStrictRoute.get()) {
        strict_route = true
    }
    if (PlatformInfo.isLinux && !networkManager && DataStore.tunAutoRedirect.get()) {
        auto_redirect = true
    }
}

internal actual val localDNSSupportRaw: Boolean = true

internal actual val anchorDeviceName: String
    get() = error("nope")

internal actual val protectPath: String = PROTECT_PATH_NAME
