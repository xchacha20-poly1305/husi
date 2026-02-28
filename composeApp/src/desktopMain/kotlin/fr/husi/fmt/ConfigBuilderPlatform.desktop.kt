package fr.husi.fmt

import fr.husi.database.DataStore
import fr.husi.repository.repo

internal actual fun SingBoxOptions.Inbound_TunOptions.applyPlatformConfig() {
    auto_route = true
    if (DataStore.tunStrictRoute) {
        strict_route = true
    }
    if (repo.isLinux) {
        auto_redirect = true
    }
}