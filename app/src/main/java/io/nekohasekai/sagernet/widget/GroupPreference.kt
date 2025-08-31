package io.nekohasekai.sagernet.widget

import io.nekohasekai.sagernet.database.SagerDatabase
import rikka.preference.SimpleMenuPreference

fun SimpleMenuPreference.setGroupBean() {
    val groups = SagerDatabase.groupDao.allGroups()

    entries = Array(groups.size) { groups[it].displayName() }
    entryValues = Array(groups.size) { groups[it].id.toString() }

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