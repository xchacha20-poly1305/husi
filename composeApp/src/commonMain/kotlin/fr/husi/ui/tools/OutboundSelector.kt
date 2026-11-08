package fr.husi.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.husi.compose.DropDownSelector
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.outbound
import fr.husi.resources.outbound_default
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun OutboundSelector(
    selectedTag: String,
    onSelect: (String) -> Unit,
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    val defaultLabel = stringResource(Res.string.outbound_default)
    DropDownSelector(
        modifier = modifier,
        label = { Text(stringResource(Res.string.outbound)) },
        value = selectedTag,
        values = tags,
        onValueChange = onSelect,
        displayValue = { tag -> tag.ifEmpty { defaultLabel } },
    )
}
