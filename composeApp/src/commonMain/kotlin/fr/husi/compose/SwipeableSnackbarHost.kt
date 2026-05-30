package fr.husi.compose

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

/**
 * A [SnackbarHost] whose snackbars can be dismissed by swiping horizontally in either direction.
 * Drop-in replacement for [SnackbarHost] with default snackbar content.
 */
@Composable
fun SwipeableSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    ) { data ->
        // Fresh state per SnackbarData; otherwise a dismissed state would instantly hide the next snackbar.
        key(data) {
            val swipeState = rememberSwipeToDismissBoxState()
            SwipeToDismissBox(
                state = swipeState,
                backgroundContent = {},
                onDismiss = { data.dismiss() },
            ) {
                Snackbar(data)
            }
        }
    }
}
