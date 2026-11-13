package fr.husi.ui.clash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.clash_empty
import fr.husi.resources.clash_unsupported
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.clashStatus(
    loading: Boolean,
    error: String,
    unsupported: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!loading && error.isEmpty()) return

    item {
        Column(
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) LinearWavyProgressIndicator()
            if (error.isNotEmpty()) {
                val text = if (unsupported) {
                    stringResource(Res.string.clash_unsupported)
                } else {
                    error
                }
                Text(text)
            }
        }
    }
}

@Composable
internal fun EmptyClashList(modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.clash_empty),
        modifier = modifier.padding(16.dp),
    )
}

internal fun LazyListScope.clashCard(
    key: Any,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}
