package io.nekohasekai.sagernet.widget

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.getSystemService
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.Logs

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

    override fun onCreateDialogView(context: Context): View {
        val preference = preference as EditTextPreference
        val layoutResId = preference.dialogLayoutResource

        val view = layoutInflater.inflate(
            if (layoutResId != 0) layoutResId else R.layout.m3_dialog_edit_text,
            null
        )

        val editText = view.findViewById<EditText>(android.R.id.edit)
        if (editText == null) {
            error("Dialog view must contain an EditText with id @android:id/edit")
        }
        this.editText = editText

        return view
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        val localEditText = this.editText ?: return
        val preference = preference as EditTextPreference

        localEditText.setText(preference.text)

        try {
            val clazz = EditTextPreference::class.java
            val listenerField = clazz.getDeclaredField("mOnBindEditTextListener")
            listenerField.isAccessible = true
            val onBindListener = listenerField.get(preference)
            if (onBindListener != null) {
                (onBindListener as EditTextPreference.OnBindEditTextListener)
                    .onBindEditText(localEditText)
            }
        } catch (e: Exception) {
            Logs.e(e)
        }

        localEditText.post {
            localEditText.setSelection(localEditText.text?.length ?: 0)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val preference = preference as EditTextPreference

        val contentView = onCreateDialogView(context)
        onBindDialogView(contentView)

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(preference.dialogTitle ?: preference.title)
            .setIcon(preference.dialogIcon ?: preference.icon)
            .setPositiveButton(
                preference.positiveButtonText ?: getString(android.R.string.ok),
                this,
            )
            .setNegativeButton(
                preference.negativeButtonText ?: getString(android.R.string.cancel),
                this,
            )
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
        if (!positiveResult) return
        val preference = preference as EditTextPreference
        val value = editText?.text?.toString()
        if (preference.callChangeListener(value)) {
            preference.text = value
        }
    }
}