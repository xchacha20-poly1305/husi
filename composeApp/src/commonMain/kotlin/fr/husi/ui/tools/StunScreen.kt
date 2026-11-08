package fr.husi.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.ButtonDefaults
import fr.husi.compose.material3.Card
import fr.husi.compose.material3.ScrollableDropdownMenuPopup
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.cancel
import fr.husi.resources.error
import fr.husi.resources.error_title
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.nat_stun_server_hint
import fr.husi.resources.start
import fr.husi.resources.stun_test
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun StunScreenContent(
    viewModel: StunPresenter,
    targetConnected: Boolean,
    onBackPress: () -> Unit,
    targetMenu: @Composable (onDismiss: () -> Unit) -> Unit,
    outboundSelector: @Composable (String, (String) -> Unit, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var alertMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is StunScreenUiEvent.Alert -> alertMessage = event.message
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expandMenu by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                hazeState = null,
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                title = { Text(stringResource(Res.string.stun_test)) },
                actions = {
                    CapsuleActionButton {
                        Box {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.more_vert),
                                contentDescription = stringResource(Res.string.more),
                                onClick = { expandMenu = true },
                            )
                            ScrollableDropdownMenuPopup(
                                expanded = expandMenu,
                                onDismissRequest = { expandMenu = false },
                            ) {
                                targetMenu { expandMenu = false }
                            }
                        }
                    }
                },
                windowInsets =
                    windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().paddingExceptBottom(innerPadding),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                OutlinedTextField(
                    value = uiState.server,
                    onValueChange = { viewModel.setServer(it) },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    label = { Text(stringResource(Res.string.nat_stun_server_hint)) },
                    singleLine = true,
                )
                if (targetConnected) {
                    outboundSelector(
                        uiState.outboundTag,
                        viewModel::setOutboundTag,
                        Modifier.fillMaxWidth().padding(8.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (uiState.isDoing) {
                    Button(
                        onClick = viewModel::cancel,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                    ) {
                        Text(stringResource(Res.string.cancel))
                    }
                } else {
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.doTest(targetConnected)
                        },
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(Res.string.start))
                    }
                }
            }

            uiState.report?.let { report ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    StunReportContent(report, uiState.isDoing)
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    alertMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            confirmButton = {
                TextButton(stringResource(Res.string.back)) {
                    alertMessage = null
                }
            },
            icon = { Icon(vectorResource(Res.drawable.error), null) },
            title = { Text(stringResource(Res.string.error_title)) },
            text = { Text(message) },
        )
    }
}
