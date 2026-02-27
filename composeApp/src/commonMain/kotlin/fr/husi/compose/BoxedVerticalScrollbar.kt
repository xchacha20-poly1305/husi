package fr.husi.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.oikvpqya.compose.fastscroller.ScrollbarAdapter
import io.github.oikvpqya.compose.fastscroller.ScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar

/** Use it with Row */
@Composable
fun BoxedVerticalScrollbar(
    adapter: ScrollbarAdapter,
    style: ScrollbarStyle,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enablePressToScroll: Boolean = true,
    indicator: @Composable (position: Float, isVisible: Boolean) -> Unit = { _, _ -> },
) {
    Box {
        VerticalScrollbar(
            adapter = adapter,
            style = style,
            modifier = modifier,
            reverseLayout = reverseLayout,
            interactionSource = interactionSource,
            enablePressToScroll = enablePressToScroll,
            indicator = indicator,
        )
    }
}
