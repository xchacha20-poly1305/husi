package io.nekohasekai.sagernet.widget

import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ui.StringOrRes

class UndoSnackbarManager<in T>(
    private val snackbar: SnackbarAdapter,
    private val callback: Interface<T>,
) {

    /**
     * @param undo Callback for undoing removals.
     * @param commit Callback for committing removals.
     */
    interface Interface<in T> {
        fun undo(actions: List<Pair<Int, T>>)
        fun commit(actions: List<Pair<Int, T>>)
    }

    /**
     * Adapt compose and view.
     */
    interface SnackbarAdapter {
        fun setMessage(message: StringOrRes): SnackbarAdapter
        fun setAction(actionLabel: StringOrRes): SnackbarAdapter
        fun setOnAction(block: () -> Unit): SnackbarAdapter
        fun setOnDismiss(block: () -> Unit): SnackbarAdapter

        fun show()

        fun flush()
    }

    private val recycleBin = ArrayList<Pair<Int, T>>()
    private var programmaticDismiss = false

    fun remove(items: Collection<Pair<Int, T>>) {
        programmaticDismiss = true
        snackbar.flush()
        programmaticDismiss = false

        recycleBin.addAll(items)
        val count = recycleBin.size

        snackbar.setMessage(StringOrRes.PluralsRes(R.plurals.removed, count, count))
            .setAction(StringOrRes.Res(R.string.undo))
            .setOnAction {
                callback.undo(recycleBin.reversed())
                recycleBin.clear()
            }
            .setOnDismiss {
                if (!programmaticDismiss) {
                    commitAndClear()
                }
            }
            .show()
    }

    fun remove(vararg items: Pair<Int, T>) = remove(items.toList())

    fun flush() {
        commitAndClear()
        programmaticDismiss = true
        snackbar.flush()
        programmaticDismiss = false
    }

    private fun commitAndClear() {
        if (recycleBin.isNotEmpty()) {
            callback.commit(recycleBin.toList())
            recycleBin.clear()
        }
    }
}
