@file:OptIn(ExperimentalLayoutApi::class)

package fr.husi.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.husi.compose.rememberScrollHideState
import fr.husi.compose.setPlainText
import fr.husi.compose.BoxedVerticalScrollbar
import kotlinx.coroutines.launch
import fr.husi.resources.*
import fr.husi.libcore.Libcore
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DashboardStatusScreen(
    modifier: Modifier = Modifier,
    uiState: DashboardState,
    bottomPadding: Dp,
    selectClashMode: (mode: String) -> Unit,
    onCopySuccess: () -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val visible by rememberScrollHideState(scrollState)

    LaunchedEffect(visible) {
        onVisibleChange(visible)
    }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = bottomPadding + 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.status_status),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.status_memory))
                    Text(Libcore.formatMemoryBytes(uiState.memory))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.status_goroutines))
                    Text(uiState.goroutines.toString())
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.source_address),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "IPv4",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val text = uiState.ipv4 ?: stringResource(Res.string.no_statistics)
                    Text(
                        text = text,
                        modifier = Modifier.clickable {
                            scope.launch {
                                clipboard.setPlainText(text)
                            }
                            onCopySuccess()
                        },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmallEmphasized,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "IPv6",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val text = uiState.ipv6 ?: stringResource(Res.string.no_statistics)
                    Text(
                        text = text,
                        modifier = Modifier.clickable {
                            scope.launch {
                                clipboard.setPlainText(text)
                            }
                            onCopySuccess()
                        },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmallEmphasized,
                    )
                }
            }
        }

        if (uiState.clashModes.isNotEmpty()) ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.clash_mode),
                    style = MaterialTheme.typography.titleMedium,
                )
                uiState.clashModes.forEach { mode ->
                    val selected = mode == uiState.selectedClashMode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Button(
                            onClick = { selectClashMode(mode) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !selected,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            ),
                            border = if (!selected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            } else {
                                null
                            },
                        ) {
                            Text(mode)
                        }
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.network_interfaces),
                    style = MaterialTheme.typography.titleMedium,
                )
                SelectionContainer {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        uiState.networkInterfaces.forEach { interfaceInfo ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = interfaceInfo.name,
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                )
                                for (address in interfaceInfo.addresses) {
                                    Text(
                                        text = address,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        }

        BoxedVerticalScrollbar(
            modifier = Modifier.fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = scrollState),
            style = defaultMaterialScrollbarStyle().copy(
                thickness = 12.dp,
            ),
        )
    }
}
