package io.nekohasekai.sagernet.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.TypedArrayUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.setPadding
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr

class ColorPickerPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = TypedArrayUtils.getAttr(
        context,
        androidx.preference.R.attr.editTextPreferenceStyle,
        android.R.attr.editTextPreferenceStyle
    )
) : Preference(
    context, attrs, defStyle
) {

    private var initialized = false

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val widgetFrame = holder.findViewById(android.R.id.widget_frame) as LinearLayout

        if (!initialized) {
            initialized = true

            widgetFrame.addView(
                createColorSwatchView(
                    context.getColorAttr(androidx.appcompat.R.attr.colorPrimary),
                    48,
                    0,
                )
            )
            widgetFrame.visibility = View.VISIBLE
        }
    }

    private fun createColorSwatchView(color: Int, sizeDp: Int, paddingDp: Int): ImageView {
        val size = dp2px(sizeDp)
        val paddingSize = dp2px(paddingDp)

        return ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            setPadding(paddingSize)
            setImageDrawable(generateColorDrawer(resources, color))
        }
    }

    private fun generateColorDrawer(res: Resources, color: Int): Drawable {
        val circle = ResourcesCompat.getDrawable(
            res,
            R.drawable.ic_baseline_fiber_manual_record_24,
            null,
        )!!
        DrawableCompat.setTint(circle.mutate(), color)
        return circle
    }

    override fun onClick() {
        super.onClick()

        lateinit var dialog: AlertDialog

        val grid = GridLayout(context).apply {
            columnCount = 4

            val colors = context.resources.getIntArray(R.array.material_colors)
            for ((i, color) in colors.withIndex()) {
                val themeId = i + 1
                val view = createColorSwatchView(color, 64, 0).apply {
                    setOnClickListener {
                        persistInt(themeId)
                        dialog.dismiss()
                        callChangeListener(themeId)
                    }
                }
                addView(view)
            }

        }

        @Suppress("AssignedValueIsNeverRead") // Will be used on view click
        dialog = MaterialAlertDialogBuilder(context).setTitle(title)
            .setView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(grid)
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}