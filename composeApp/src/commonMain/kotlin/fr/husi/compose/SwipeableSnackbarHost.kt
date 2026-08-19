package fr.husi.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

@Composable
fun SwipeableSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = navigationBarsAlwaysInsets(),
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.windowInsetsPadding(windowInsets),
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
