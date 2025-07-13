package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ScrollingView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton

class HideOnBottomFabBehavior @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FloatingActionButton.Behavior(context, attrs) {
    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: FloatingActionButton,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int,
    ): Boolean {
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL || super.onStartNestedScroll(
            coordinatorLayout,
            child,
            directTargetChild,
            target,
            axes,
            type,
        )
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: FloatingActionButton,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        super.onNestedScroll(
            coordinatorLayout,
            child,
            target,
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            type,
            consumed,
        )

        if (dyConsumed < 0 || dyUnconsumed < 0) {
            if (!child.isVisible) {
                child.show()
                return
            }
        }

        val isScrollingDown = dyConsumed > 0 || dyUnconsumed > 0
        if (!isScrollingDown) {
            return
        }
        if (target !is ScrollingView && target !is ScrollView) {
            return
        }
        val hasReachedBottom = !target.canScrollVertically(1)
        if (hasReachedBottom) {
            child.hide(object : FloatingActionButton.OnVisibilityChangedListener() {
                override fun onHidden(fab: FloatingActionButton) {
                    super.onHidden(fab)
                    fab.visibility = View.INVISIBLE
                }
            })
        }
    }
}