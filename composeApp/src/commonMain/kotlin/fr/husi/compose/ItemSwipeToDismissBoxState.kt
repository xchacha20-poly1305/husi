package fr.husi.compose

import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * [androidx.compose.material3.rememberSwipeToDismissBoxState] is saveable,
 * which is not suitable in LazyList.
 */
@Composable
fun rememberSwipeToDismissBoxStateUnsaveable(
    key: Any?,
    initialValue: SwipeToDismissBoxValue = SwipeToDismissBoxValue.Settled,
    positionalThreshold: (totalDistance: Float) -> Float =
        SwipeToDismissBoxDefaults.positionalThreshold,
): SwipeToDismissBoxState = remember(key) {
    SwipeToDismissBoxState(initialValue, positionalThreshold)
}

// Note: IDEA declares that "saveable" is typo and should be replaced with "savable". Suppress it.
