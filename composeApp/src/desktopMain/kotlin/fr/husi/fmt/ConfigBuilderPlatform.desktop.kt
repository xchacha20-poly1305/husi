package fr.husi.fmt

import fr.husi.database.DataStore
import fr.husi.ktx.blankAsNull
import fr.husi.platform.PlatformInfo

internal actual fun SingBoxOptions.Inbound_TunOptions.applyPlatformConfig() {
    auto_route = true
    interface_name = DataStore.tunInterfaceName.getBlocking().blankAsNull()
    if (DataStore.tunStrictRoute.getBlocking()) {
        strict_route = true
    }
    if (PlatformInfo.isLinux && DataStore.tunAutoRedirect.getBlocking()) {
        auto_redirect = true
    }
}

internal actual val localDNSSupportRaw: Boolean = true

internal actual val anchorDeviceName: String
    get() = error("nope")

internal actual val protectPath: String
    get() = error("nope")
