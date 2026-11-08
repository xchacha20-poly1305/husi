package fr.husi.compose

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/**
 * Ownership of the focus an anchor holds, so it can be handed back after the window took it away.
 *
 * Motivation: `Modifier.onPreviewKeyEvent` are dispatched to the focused node and its ancestors,
 * so a screen whose shortcuts live on such a node goes silent whenever nothing inside it is focused.
 */
@Stable
class FocusRestoreState internal constructor() {

    internal val requester = FocusRequester()

    var isAttached by mutableStateOf(false)

    private var hasFocus by mutableStateOf(false)

    private var isLastOwner = false

    internal fun onFocusChanged(hasFocus: Boolean) {
        this.hasFocus = hasFocus
        isLastOwner = !hasFocus
    }

    internal fun isRestoreOwed(): Boolean = isLastOwner && !hasFocus

    fun restore() {
        if (!isAttached) return
        runCatching { requester.requestFocus() }
    }

}

@Composable
fun rememberFocusRestoreState(): FocusRestoreState = remember { FocusRestoreState() }

@Composable
fun Modifier.focusRestoreAnchor(state: FocusRestoreState, canHoldFocus: Boolean): Modifier {
    val windowInfo = LocalWindowInfo.current
    val currentCanHoldFocus by rememberUpdatedState(canHoldFocus)
    LaunchedEffect(state, windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .drop(1) // At composition time, it is not a change.
            .filter { isWindowFocused -> isWindowFocused }
            .collect {
                if (currentCanHoldFocus && state.isRestoreOwed()) {
                    state.restore()
                }
            }
    }
    return this
        // The requester has to wrap the focus target: requestFocus() only visits children.
        .focusRequester(state.requester)
        .onPlaced { state.isAttached = true }
        .onFocusChanged { state.onFocusChanged(it.hasFocus) }
        .focusable()
}
