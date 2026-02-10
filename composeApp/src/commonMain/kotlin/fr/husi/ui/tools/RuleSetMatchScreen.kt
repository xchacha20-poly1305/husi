package fr.husi.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.TextButton
import fr.husi.compose.paddingExceptBottom
import fr.husi.ui.getStringOrRes
import fr.husi.resources.*

@Composable
internal fun RuleSetMatchScreen(
    modifier: Modifier = Modifier,
    viewModel: RuleSetMatchScreenViewModel = viewModel { RuleSetMatchScreenViewModel() },
    onBackPress: () -> Unit,
) {
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var alert by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is RuleSetMatchUiEvent.Alert -> alert = getStringOrRes(event.message)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.rule_set_match)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
        ) {
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
                        onValueChange = { viewModel.setKeyword(it) },
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
                    onClick = { viewModel.scan() },
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
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(
                        items = uiState.matched,
                        key = { it },
                    ) { text ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                                .clickable {}, // make ripple
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                SelectionContainer {
                                    Text(text)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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

@Preview
@Composable
private fun PreviewRuleSetMatch() {
    val viewModel = viewModel<RuleSetMatchScreenViewModel>()
    RuleSetMatchScreen(
        viewModel = viewModel,
        onBackPress = {},
    )
}
