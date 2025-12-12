@file:OptIn(ExperimentalLayoutApi::class)

package io.nekohasekai.sagernet.ui.dashboard

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.extraBottomPadding
import io.nekohasekai.sagernet.compose.rememberScrollHideState
import io.nekohasekai.sagernet.compose.setPlainText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DashboardStatusScreen(
    modifier: Modifier = Modifier,
    uiState: DashboardState,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(extraBottomPadding())
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    text = stringResource(R.string.status_status),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.status_memory))
                    Text(Formatter.formatFileSize(LocalContext.current, uiState.memory))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.status_goroutines))
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
                    text = stringResource(R.string.source_address),
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
                    val text = uiState.ipv4 ?: stringResource(R.string.no_statistics)
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
                    val text = uiState.ipv6 ?: stringResource(R.string.no_statistics)
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
                    text = stringResource(R.string.clash_mode),
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
                    text = stringResource(R.string.network_interfaces),
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

        Spacer(
            modifier = Modifier.padding(
                WindowInsets.navigationBarsIgnoringVisibility
                    .asPaddingValues()
                    .calculateBottomPadding(),
            ),
        )
    }
}