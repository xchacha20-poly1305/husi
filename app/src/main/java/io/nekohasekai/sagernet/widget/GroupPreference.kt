package io.nekohasekai.sagernet.widget

import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.mapX
import rikka.preference.SimpleMenuPreference

fun SimpleMenuPreference.setGroupBean() {
    val groups = SagerDatabase.groupDao.allGroups()

    entries = groups.mapX { it.displayName() }.toTypedArray()
    entryValues = groups.mapX { "${it.id}" }.toTypedArray()

    // Instead of useSimpleSummaryProvider, this can show unset group name.

    fun getSummary(value: String): CharSequence {
        if (value.isNotBlank() && value != "0") {
            SagerDatabase.groupDao.getById(value.toLong())?.displayName()?.let {
                return it
            }
        }
        return entries[value.toIntOrNull() ?: 0] ?: entries[0]
    }
    summary = getSummary(value ?: "")

    setOnPreferenceChangeListener { _, newValue ->
        summary = getSummary(newValue as String)
        true
    }
}