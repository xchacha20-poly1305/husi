package fr.husi.fmt

import fr.husi.database.DataStore
import fr.husi.ktx.blankAsNull
import fr.husi.platform.PlatformInfo

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