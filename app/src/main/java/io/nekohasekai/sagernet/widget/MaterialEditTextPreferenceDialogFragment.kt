package io.nekohasekai.sagernet.widget

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.getSystemService
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R

/**
 * Inspired by https://github.com/F0x1d/LogFox/blob/40cea7614093aa9c1d97222834331acebda20da4/core/ui/src/main/kotlin/com/f0x1d/logfox/ui/view/PreferenceExt.kt#L11
 */
class MaterialEditTextPreferenceDialogFragment : PreferenceDialogFragmentCompat() {

    private var editText: EditText? = null

    companion object {
        const val TAG = "MaterialEditTextPreferenceDialogFragment"

        fun newInstance(key: String): MaterialEditTextPreferenceDialogFragment {
            val fragment = MaterialEditTextPreferenceDialogFragment()
            val args = Bundle(1).apply {
                putString(ARG_KEY, key)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialogView(context: Context): View? {
        val preference = preference as EditTextPreference
        val layoutResId = preference.dialogLayoutResource

        val dialogView = LayoutInflater.from(context).inflate(
            if (layoutResId != 0) layoutResId else R.layout.m3_dialog_edit_text,
            null,
        )

        val editText = dialogView.findViewById<EditText>(android.R.id.edit)
        if (editText == null) {
            error("Dialog view must contain an EditText with id @android:id/edit")
        }
        this.editText = editText

        return dialogView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val contentView = onCreateDialogView(context)

        val preference = preference as EditTextPreference
        editText?.setText(preference.text)
        editText?.let {
            it.setSelection(it.text?.length ?: 0)
        }

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(preference.title)
            .setIcon(preference.icon)
            .setMessage(preference.dialogMessage)
            .setPositiveButton(android.R.string.ok, this)
            .setNegativeButton(android.R.string.cancel, this)
            .setView(contentView)

        val dialog = builder.create()

        dialog.setOnShowListener {
            editText?.apply {
                requestFocus()
                postDelayed({
                    val imm = context.getSystemService<InputMethodManager>()
                    imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }, 120)
            }
        }

        return dialog
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val preference = preference as EditTextPreference
            val value = editText?.text?.toString()
            if (preference.callChangeListener(value)) {
                preference.text = value
            }
        }
    }

}