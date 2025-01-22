package io.nekohasekai.sagernet.widget

import androidx.preference.MultiSelectListPreference

fun MultiSelectListPreference.updateSummary(value: Set<String> = values) {
    summary = if (value.isEmpty()) {
        context.getString(androidx.preference.R.string.not_set)
    } else {
        value.joinToString(",")
    }
}