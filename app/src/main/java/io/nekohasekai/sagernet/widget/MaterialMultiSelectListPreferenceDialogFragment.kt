package io.nekohasekai.sagernet.widget

import android.app.Dialog
import android.os.Bundle
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MaterialMultiSelectListPreferenceDialogFragment : PreferenceDialogFragmentCompat() {

    private val selectedItems = mutableSetOf<String>()

    companion object {
        const val TAG = "MaterialMultiSelectListPreferenceDialogFragment"

        private const val SAVE_STATE_VALUES =
            "MaterialMultiSelectListPreferenceDialogFragment.values"

        fun newInstance(key: String): MaterialMultiSelectListPreferenceDialogFragment {
            val fragment = MaterialMultiSelectListPreferenceDialogFragment()
            val args = Bundle(1).apply {
                putString(ARG_KEY, key)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val preference = preference as MultiSelectListPreference
            selectedItems.clear()
            selectedItems.addAll(preference.values)
        } else {
            selectedItems.clear()
            savedInstanceState.getStringArray(SAVE_STATE_VALUES)?.let {
                selectedItems.addAll(it)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArray(SAVE_STATE_VALUES, selectedItems.toTypedArray())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val preference = preference as MultiSelectListPreference
        val context = requireContext()

        val checkedItems = preference.entryValues.map {
            selectedItems.contains(it.toString())
        }.toBooleanArray()

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(preference.title)
            .setIcon(preference.icon)
            .setMultiChoiceItems(
                preference.entries,
                checkedItems,
            ) { dialog, which, isChecked ->
                val value = preference.entryValues[which].toString()
                if (isChecked) {
                    selectedItems.add(value)
                } else {
                    selectedItems.remove(value)
                }
            }
            .setPositiveButton(android.R.string.ok, this)
            .setNegativeButton(android.R.string.cancel, this)

        if (!preference.dialogMessage.isNullOrEmpty()) {
            builder.setMessage(preference.dialogMessage)
        }

        return builder.create()
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) return
        val preference = preference as MultiSelectListPreference
        if (preference.callChangeListener(selectedItems)) {
            preference.values = selectedItems
        }
    }
}