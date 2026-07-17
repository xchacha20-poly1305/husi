package fr.husi.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.cancel
import fr.husi.resources.ok
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T> OrderedMultiselectDialog(
    selected: List<T>,
    values: List<T>,
    onValueChange: (List<T>) -> Unit,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    valueToText: (T) -> String,
) {
    val allValues = remember(selected, values) { (values + selected).distinct() }
    val selectedValues = remember(selected, allValues) {
        mutableStateListOf<T>().also { selectedValues ->
            selectedValues.addAll(selected.distinct().filter { it in allValues })
        }
    }
    val onConfirm = {
        onValueChange(selectedValues.toList())
        onDismissRequest()
    }

    OrderedMultiselectAlertDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        buttons = {
            TextButton(stringResource(Res.string.cancel), onClick = onDismissRequest)
            TextButton(stringResource(Res.string.ok), onClick = onConfirm)
        },
    ) {
        val lazyListState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = lazyListState,
        ) {
            items(allValues) { item ->
                val selectedIndex = selectedValues.indexOf(item)
                val isSelected = selectedIndex >= 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = isSelected,
                            enabled = true,
                            role = Role.Checkbox,
                        ) {
                            if (isSelected) {
                                selectedValues.removeAt(selectedIndex)
                            } else {
                                selectedValues.add(item)
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OrderedSelectIndicator(
                        selected = isSelected,
                        number = selectedIndex + 1,
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    Text(
                        text = valueToText(item),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderedSelectIndicator(
    selected: Boolean,
    number: Int,
) {
    val indicatorShape = RoundedCornerShape(6.dp)
    val fillProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (selected) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        },
        label = "indicatorFill",
    )

    var lastNumber by remember { mutableIntStateOf(1) }
    if (number > 0) lastNumber = number

    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 1f - fillProgress.coerceIn(0f, 1f) }
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = indicatorShape,
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = fillProgress
                    scaleY = fillProgress
                    alpha = fillProgress.coerceIn(0f, 1f)
                }
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = indicatorShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = lastNumber,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut())
                    } else {
                        (slideInVertically { -it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { it / 2 } + fadeOut())
                    } using SizeTransform(clip = false)
                },
                label = "indicatorNumber",
            ) { order ->
                Text(
                    text = order.toString(),
                    color = AlertDialogDefaults.containerColor,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OrderedMultiselectAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    buttons: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                DialogContentColorTextStyle(
                    contentColor = AlertDialogDefaults.titleContentColor,
                    textStyle = MaterialTheme.typography.headlineSmall,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 24.dp,
                                top = 24.dp,
                                end = 24.dp,
                                bottom = 16.dp,
                            ),
                    ) {
                        title()
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    content()
                }
                DialogContentColorTextStyle(
                    contentColor = MaterialTheme.colorScheme.primary,
                    textStyle = MaterialTheme.typography.labelLarge,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 24.dp,
                                top = 16.dp,
                                end = 24.dp,
                                bottom = 24.dp,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                            content = buttons,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogContentColorTextStyle(
    contentColor: Color,
    textStyle: TextStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTextStyle provides LocalTextStyle.current.merge(textStyle),
        content = content,
    )
}
