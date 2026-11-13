package fr.husi.ui.clash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Card
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleSearchInputField
import fr.husi.compose.CapsuleSearchTopBar
import fr.husi.compose.ProvidePreferenceLocals
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Text
import fr.husi.compose.theme.HusiScrollbarDefaults
import fr.husi.compose.withNavigation
import fr.husi.remote.ClashRule
import fr.husi.resources.Res
import fr.husi.resources.cancel
import fr.husi.resources.clash_enabled
import fr.husi.resources.clash_rule_unavailable
import fr.husi.resources.clash_rules
import fr.husi.resources.clash_state_unknown
import fr.husi.resources.clash_unsupported
import fr.husi.resources.close
import fr.husi.resources.disabled
import fr.husi.resources.refresh
import fr.husi.resources.search
import fr.husi.resources.update
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun ClashRulesScreen(
    state: ClashRulesState,
    onRefresh: () -> Unit,
    onToggle: (ClashRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val focusManager = LocalFocusManager.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val inputField: @Composable () -> Unit = {
        CapsuleSearchInputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { focusManager.clearFocus() },
            placeholder = { Text(stringResource(Res.string.clash_rules)) },
            leadingIcon = { Icon(vectorResource(Res.drawable.search), stringResource(Res.string.search)) },
            trailingIcon = if (searchBarState.currentValue == SearchBarValue.Expanded) {
                {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.cancel),
                        onClick = {
                            textFieldState.setTextAndPlaceCursorAtEnd("")
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                    )
                }
            } else {
                null
            },
        )
    }
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleSearchTopBar(
                hazeState = hazeState,
                inputField = inputField,
                actions = {
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.update),
                            contentDescription = stringResource(Res.string.refresh),
                            enabled = !state.loading,
                            onClick = onRefresh,
                        )
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fadingEdge(listState),
                    contentPadding = contentPadding,
                ) {
                    clashStatus(state.loading, state.error, state.unsupported)
                    rulesList(state, textFieldState.text.toString(), onToggle)
                }
                BoxedVerticalScrollbar(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState),
                    style = HusiScrollbarDefaults.style(),
                )
            }
        }
    }
    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField,
    ) {
        ProvidePreferenceLocals {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                rulesList(state, textFieldState.text.toString(), onToggle)
            }
        }
    }
}

private fun LazyListScope.rulesList(
    state: ClashRulesState,
    query: String,
    onToggle: (ClashRule) -> Unit,
) {
    val visible = state.rules.filter { rule ->
        listOf(rule.type, rule.payload, rule.target).any {
            it.contains(query, ignoreCase = true)
        }
    }
    if (visible.isEmpty() && !state.loading) {
        item {
            EmptyClashList()
        }
    }
    // Rules can be numerous; keep each rule as a separate lazy item.
    for ((index, rule) in visible.withIndex()) {
        item(key = "rule-$index") {
            val summary: @Composable () -> Unit = {
                Column {
                    Text("${rule.type} · ${rule.target}")
                    Text(
                        stringResource(
                            if (rule.disabled) {
                                Res.string.disabled
                            } else {
                                Res.string.clash_enabled
                            },
                        ),
                    )
                    when {
                        state.uncertain[rule.uuid] == true -> Text(stringResource(Res.string.clash_state_unknown))

                        state.toggleUnsupported -> Text(stringResource(Res.string.clash_unsupported))

                        state.unavailable[rule.uuid] == true -> Text(stringResource(Res.string.clash_rule_unavailable))
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(rule.payload.ifBlank { rule.type })
                    },
                    supportingContent = summary,
                    trailingContent = if (rule.toggleable) {
                        {
                            Switch(
                                modifier = Modifier.semantics {
                                    contentDescription = rule.payload.ifBlank { rule.type }
                                },
                                checked = !rule.disabled,
                                onCheckedChange = { onToggle(rule) },
                                enabled =
                                    !state.loading
                                        && !state.unsupported
                                        && state.pending[rule.uuid] != true
                                        && state.unavailable[rule.uuid] != true
                                        && state.uncertain[rule.uuid] != true
                                        && !state.toggleUnsupported,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
