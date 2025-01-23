package io.nekohasekai.sagernet.widget

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import rikka.preference.SimpleMenuPreference

/**
 * Allow this SimpleMenu to provide custom outbound.
 * @param position The position of outbound.
 * @param launch Launch outbound picking UI.
 */
fun SimpleMenuPreference.setOutbound(position: String, launch: () -> Unit) {
    setOnPreferenceChangeListener { _, newValue ->
        if (newValue.toString() == position) {
            launch()
            false
        } else {
            summary = entries[newValue.toString().toInt()]
            true
        }
    }
}

fun SimpleMenuPreference.updateOutboundSummary() {
    var sum: CharSequence? = null

    val outbound = DataStore.profileCacheStore.getLong(key + "Long") ?: 0
    if (outbound > 0) {
        sum = ProfileManager.getProfile(outbound)?.displayName()
    }

    if (sum == null) sum = entries[value.toInt()]
    summary = sum
}
