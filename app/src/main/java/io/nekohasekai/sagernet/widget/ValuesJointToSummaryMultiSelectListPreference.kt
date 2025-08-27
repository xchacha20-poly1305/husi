package io.nekohasekai.sagernet.widget

import androidx.preference.MultiSelectListPreference
import android.content.Context
import android.util.AttributeSet
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString

/**
 * @param separator the separator for [joinToString]
 */
class ValuesJointToSummaryMultiSelectListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    defStyleRes: Int = 0,
    var separator: String = "\n",
) : MultiSelectListPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        summaryProvider = MultiSelectListSummaryProvider
    }

    private object MultiSelectListSummaryProvider :
        SummaryProvider<ValuesJointToSummaryMultiSelectListPreference> {
        override fun provideSummary(preference: ValuesJointToSummaryMultiSelectListPreference): CharSequence {
            return preference.values?.takeIf { it.isNotEmpty() }?.joinToString(preference.separator)
                ?: preference.context.getString(androidx.preference.R.string.not_set)
        }
    }
}
