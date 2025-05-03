package io.nekohasekai.sagernet.ktx

import android.graphics.Rect
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainActivity

class FixedLinearLayoutManager(private val recyclerView: RecyclerView) :
    LinearLayoutManager(recyclerView.context, RecyclerView.VERTICAL, false) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (_: IndexOutOfBoundsException) {
        }
    }

    private var listenerDisabled = false
    val showBottomBar = DataStore.showBottomBar

    override fun scrollVerticallyBy(
        dx: Int, recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        // Matsuri style
        if (!showBottomBar) return super.scrollVerticallyBy(dx, recycler, state)

        // SagerNet Style
        val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
        if (listenerDisabled) return scrollRange
        val activity = recyclerView.context as? MainActivity
        if (activity == null) {
            listenerDisabled = true
            return scrollRange
        }

        val overscroll = dx - scrollRange
        if (overscroll > 0) {
            val view =
                (recyclerView.findViewHolderForAdapterPosition(findLastVisibleItemPosition())
                    ?: return scrollRange).itemView
            val itemLocation = Rect().also { view.getGlobalVisibleRect(it) }
            val fabLocation = Rect().also { activity.binding.fab.getGlobalVisibleRect(it) }
            if (!itemLocation.contains(fabLocation.left, fabLocation.top) && !itemLocation.contains(
                    fabLocation.right,
                    fabLocation.bottom
                )
            ) {
                return scrollRange
            }
            activity.binding.fab.apply {
                if (isShown) hide()
            }
        } else {
            /*val screen = Rect().also { activity.window.decorView.getGlobalVisibleRect(it) }
            val location = Rect().also { activity.stats.getGlobalVisibleRect(it) }
            if (screen.bottom < location.bottom) {
                return scrollRange
            }
            val height = location.bottom - location.top
            val mH = activity.stats.measuredHeight

            if (mH > height) {
                return scrollRange
            }*/

            activity.binding.fab.apply {
                if (!isShown) show()
            }
        }
        return scrollRange
    }

}

// usually call if DataStore.showBottomBar
fun NestedScrollView.setStatusBar(fab: FloatingActionButton) {
    setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
        val childView = v.getChildAt(0)
        if (childView == null) {
            if (!fab.isShown) fab.show()
            return@OnScrollChangeListener
        }

        val childHeight = childView.measuredHeight
        val viewHeight = v.measuredHeight
        val isScrollable = childHeight > viewHeight

        if (scrollY > oldScrollY && fab.isShown) {
            val isAtBottom = scrollY >= (childHeight - viewHeight)
            if (isAtBottom) {
                fab.hide()
            }
        } else if (scrollY < oldScrollY && !fab.isShown) {
            fab.show()
        }

        if ((!isScrollable || scrollY == 0) && !fab.isShown) {
            fab.show()
        }
    })
}