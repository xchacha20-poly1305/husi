package io.nekohasekai.sagernet.widget

import androidx.preference.EditTextPreference
import androidx.preference.Preference

object PasswordSummaryProvider : Preference.SummaryProvider<EditTextPreference> {
    override fun provideSummary(preference: EditTextPreference): CharSequence {
        val text = preference.text
        return if (text.isNullOrBlank()) {
            preference.context.getString(androidx.preference.R.string.not_set)
        } else {
            "\u2022".repeat(text.length)
        }
    }
}
