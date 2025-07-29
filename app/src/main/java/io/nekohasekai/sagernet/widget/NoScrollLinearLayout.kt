package io.nekohasekai.sagernet.widget

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager

class NoScrollLinearLayout : LinearLayoutManager {
    constructor(context: Context) : super(context)

    constructor(context: Context, orientation: Int, reverseLayout: Boolean) : super(
        context,
        orientation,
        reverseLayout,
    )

    constructor(
        context: Context,
        attrs: android.util.AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun canScrollHorizontally(): Boolean {
        return false
    }

    override fun canScrollVertically(): Boolean {
        return false
    }
}