package fr.husi.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.SwipeableSnackbarHost
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Card
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.compose.setPlainText
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.close
import fr.husi.resources.copy_success
import fr.husi.resources.destination_address
import fr.husi.resources.error
import fr.husi.resources.error_title
import fr.husi.resources.ok
import fr.husi.resources.rule_set_match
import fr.husi.resources.start
import fr.husi.ui.getStringOrRes
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun RuleSetMatchScreen(
    modifier: Modifier = Modifier,
    viewModel: RuleSetMatchScreenViewModel = viewModel { RuleSetMatchScreenViewModel() },
    onBackPress: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarState = remember { SnackbarHostState() }

    val stringCopySuccess = stringResource(Res.string.copy_success)
    val stringOK = stringResource(Res.string.ok)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                title = { Text(stringResource(Res.string.rule_set_match)) },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SwipeableSnackbarHost(snackbarState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
        ) {
            RuleSetMatchContent(
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
                onCopy = {
                    scope.launch {
                        snackbarState.showSnackbar(
                            message = stringCopySuccess,
                            actionLabel = stringOK,
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
internal fun RuleSetMatchContent(
    viewModel: RuleSetMatchScreenViewModel,
    modifier: Modifier = Modifier,
    onCopy: (suspend () -> Unit)? = null,
) {
    var alert by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is RuleSetMatchUiEvent.Alert -> alert = getStringOrRes(event.message)
            }
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = uiState.keyword,
                    onValueChange = viewModel::setKeyword,
                    label = { Text(stringResource(Res.string.destination_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = viewModel::scan,
                enabled = !uiState.isDoing,
            ) {
                Text(stringResource(Res.string.start))
            }
        }
        Spacer(Modifier.height(4.dp))

        OutlinedCard(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .weight(1f),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    items(
                        items = uiState.matched,
                        key = { it },
                    ) { text ->
                        ElevatedCard(
                            onClick = {
                                if (onCopy != null) {
                                    coroutineScope.launch {
                                        clipboard.setPlainText(text)
                                        onCopy()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(text)
                            }
                        }
                    }
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier.fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        }
    }

    if (alert != null) AlertDialog(
        onDismissRequest = { alert = null },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                alert = null
            }
        },
        icon = { Icon(vectorResource(Res.drawable.error), null) },
        title = { Text(stringResource(Res.string.error_title)) },
        text = { Text(alert!!) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleSetMatchDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onCopy: (suspend () -> Unit)? = null,
    viewModel: RuleSetMatchScreenViewModel = viewModel(
        key = "rule-set-match-dialog",
    ) { RuleSetMatchScreenViewModel() },
) {
    val density = LocalDensity.current
    val windowHeight = with(density) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    val dialogHeight = if (windowHeight > 0.dp) {
        (windowHeight * 0.8f).coerceAtMost(640.dp)
    } else {
        560.dp
    }

    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(dialogHeight),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(Res.string.rule_set_match),
                    modifier = Modifier.padding(
                        start = 24.dp,
                        top = 24.dp,
                        end = 24.dp,
                        bottom = 8.dp,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                RuleSetMatchContent(
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f),
                    onCopy = onCopy,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 24.dp,
                            top = 8.dp,
                            end = 24.dp,
                            bottom = 24.dp,
                        ),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(stringResource(Res.string.close), onDismissRequest)
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewRuleSetMatch() {
    val viewModel = viewModel<RuleSetMatchScreenViewModel>()
    RuleSetMatchScreen(
        viewModel = viewModel,
        onBackPress = {},
    )
}
