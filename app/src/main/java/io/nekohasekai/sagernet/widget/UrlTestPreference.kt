package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.widget.addTextChangedListener
import androidx.core.content.res.TypedArrayUtils
import androidx.core.view.isVisible
import androidx.preference.EditTextPreference
import com.google.android.material.textfield.TextInputLayout
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.readableMessage
import libcore.Libcore

class UrlTestPreference
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

    private lateinit var linkLayout: TextInputLayout
    private lateinit var concurrent: EditText
    private lateinit var timeout: EditText

    init {
        dialogLayoutResource = R.layout.layout_urltest_preference_dialog

        setOnBindEditTextListener {
            linkLayout = it.rootView.findViewById<TextInputLayout>(R.id.input_layout)
            fun validate() {
                val text = it.text.toString()
                if (text.isBlank()) {
                    linkLayout.isErrorEnabled = false
                    return
                }

                if (text.lines().size > 1) {
                    linkLayout.isErrorEnabled = true
                    linkLayout.error = "Unexpected new line"
                    return
                }

                try {
                    Libcore.parseURL(text)
                } catch (e: Exception) {
                    linkLayout.isErrorEnabled = true
                    linkLayout.error = e.readableMessage
                    return
                }
                linkLayout.isErrorEnabled = false
            }
            validate()
            it.addTextChangedListener { validate() }

            concurrent = it.rootView.findViewById(R.id.edit_concurrent)
            concurrent.setText(DataStore.connectionTestConcurrent.toString())
            it.rootView.findViewById<LinearLayout>(R.id.concurrent_layout)!!.isVisible = true

            timeout = it.rootView.findViewById(R.id.edit_timeout)
            timeout.setText(DataStore.connectionTestTimeout.toString())
            it.rootView.findViewById<LinearLayout>(R.id.timeout_layout)?.isVisible = true
        }

        setOnPreferenceChangeListener { _, _ ->
            concurrent.apply {
                var newConcurrent = text?.toString()?.toIntOrNull()
                if (newConcurrent == null || newConcurrent <= 0) {
                    newConcurrent = 5
                }
                DataStore.connectionTestConcurrent = newConcurrent
            }

            timeout.apply {
                var newTimeout = text?.toString()?.toIntOrNull()
                if (newTimeout == null || newTimeout > 100000) {
                    newTimeout = 3000
                }
                DataStore.connectionTestTimeout = newTimeout
            }

            true
        }
    }

}