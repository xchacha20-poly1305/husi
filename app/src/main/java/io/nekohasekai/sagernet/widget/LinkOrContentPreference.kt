package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import androidx.core.widget.addTextChangedListener
import androidx.preference.EditTextPreference
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.readableMessage
import libcore.Libcore

class LinkOrContentPreference
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
    var allowMultipleLines: Boolean = false,
) : EditTextPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        setOnBindEditTextListener {
            val linkLayout = it.rootView.findViewById<TextInputLayout>(R.id.input_layout)
            fun validate() {
                val text = it.text
                if (text.isBlank()) {
                    linkLayout.isErrorEnabled = false
                    return
                }

                val lines = text.lines()
                if (lines.size > 1 && !allowMultipleLines) {
                    linkLayout.isErrorEnabled = true
                    linkLayout.error = "Unexpected new line"
                    return
                }

                val errors = linkedSetOf<String>()
                for (link in lines) try {
                    val url = Libcore.parseURL(link)
                    when (url.scheme.lowercase()) {
                        "content" -> continue
                        "http" -> errors.add(context.getString(R.string.cleartext_http_warning))
                    }
                } catch (e: Exception) {
                    errors.add(e.readableMessage)
                }

                if (errors.isNotEmpty()) {
                    linkLayout.isErrorEnabled = true
                    linkLayout.error = errors.joinToString("\n")
                } else {
                    linkLayout.isErrorEnabled = false
                }

            }
            validate()
            it.addTextChangedListener { validate() }
        }
    }

}