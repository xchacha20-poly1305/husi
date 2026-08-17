package fr.husi.fmt

import fr.husi.database.DataStore
import fr.husi.ktx.blankAsNull
import fr.husi.platform.PlatformInfo
import java.net.InetAddress

internal actual fun SingBoxOptions.Inbound_TunOptions.applyPlatformConfig() {
    auto_route = true
    interface_name = DataStore.tunInterfaceName.blankAsNull()
    if (DataStore.tunStrictRoute) {
        strict_route = true
    }
    if (PlatformInfo.isLinux && DataStore.tunAutoRedirect) {
        auto_redirect = true
    }
}

internal actual val localDNSSupportRaw: Boolean = true

internal actual val anchorDeviceName: String
    get() = error("nope")

internal actual val protectPath: String
    get() = error("nope")
