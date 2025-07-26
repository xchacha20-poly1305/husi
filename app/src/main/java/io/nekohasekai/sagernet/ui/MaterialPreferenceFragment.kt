package io.nekohasekai.sagernet.ui

import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.widget.MaterialEditTextPreferenceDialogFragment
import io.nekohasekai.sagernet.widget.MaterialMultiSelectListPreferenceDialogFragment

abstract class MaterialPreferenceFragment : PreferenceFragmentCompat() {
    override fun onDisplayPreferenceDialog(preference: Preference) = when (preference) {
        is EditTextPreference -> {
            val dialog = MaterialEditTextPreferenceDialogFragment.newInstance(preference.key)
            @Suppress("DEPRECATION")
            dialog.setTargetFragment(this, 0)
            dialog.show(
                parentFragmentManager,
                MaterialEditTextPreferenceDialogFragment.TAG,
            )
        }

        is MultiSelectListPreference -> {
            val dialog = MaterialMultiSelectListPreferenceDialogFragment.newInstance(preference.key)
            @Suppress("DEPRECATION")
            dialog.setTargetFragment(this, 0)
            dialog.show(
                parentFragmentManager,
                MaterialEditTextPreferenceDialogFragment.TAG,
            )
        }

        else -> super.onDisplayPreferenceDialog(preference)
    }
}