package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import androidx.core.widget.addTextChangedListener
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.EditTextPreference
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.readableMessage
import libcore.Libcore

class DurationPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = TypedArrayUtils.getAttr(
        context,
        androidx.preference.R.attr.editTextPreferenceStyle,
        android.R.attr.editTextPreferenceStyle,
    ),
    defStyleRes: Int = 0,
) : EditTextPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        dialogLayoutResource = R.layout.m3_dialog_edit_text

        setOnBindEditTextListener {
            val inputLayout = it.rootView.findViewById<TextInputLayout>(R.id.input_layout)

            fun validate() {
                val text = it.text
                if (text.isBlank()) {
                    inputLayout.isErrorEnabled = false
                    return
                }

                runCatching {
                    Libcore.parseDuration(text.toString())
                }.onSuccess {
                    inputLayout.isErrorEnabled = false
                }.onFailure { e ->
                    inputLayout.isErrorEnabled = true
                    inputLayout.error = e.readableMessage
                }
            }

            validate()
            it.addTextChangedListener {
                validate()
            }
        }
    }
}
