package fr.husi.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter

@Composable
fun VerticalScrollColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var viewportHeight by remember { mutableStateOf(0) }

    Row(
        modifier = modifier.onSizeChanged { viewportHeight = it.height },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(contentPadding)
                .verticalScroll(scrollState),
            content = content,
        )

        if (viewportHeight > 0) {
            BoxedVerticalScrollbar(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .height(with(density) { viewportHeight.toDp() }),
                adapter = rememberScrollbarAdapter(scrollState = scrollState),
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 12.dp,
                ),
            )
        }
    }
}
