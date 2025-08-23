package io.nekohasekai.sagernet.widget

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.setSummaryUserInput
import rikka.preference.SimpleMenuPreference

/**
 * Allow this SimpleMenu to provide custom outbound.
 * @param position The position of outbound.
 * @param launch Launch outbound picking UI.
 */
fun SimpleMenuPreference.launchOnPosition(position: String, launch: () -> Unit) {
    setOnPreferenceChangeListener { _, newValue ->
        if (newValue.toString() == position) {
            launch()
            false
        } else {
            setSummaryUserInput(entries[newValue.toString().toInt()].toString())
            true
        }
    }
}

fun SimpleMenuPreference.setSummaryForOutbound() {
    var sum: CharSequence? = null

    val outbound = DataStore.profileCacheStore.getLong(key + "Long") ?: 0
    if (outbound > 0) {
        sum = ProfileManager.getProfile(outbound)?.displayName()
    }

    if (sum == null) sum = entries[value.toInt()]
    setSummaryUserInput(sum.toString())
}
