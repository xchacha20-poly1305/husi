package io.nekohasekai.sagernet.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.wakaztahir.codeeditor.model.CodeLang
import com.wakaztahir.codeeditor.prettify.PrettifyParser
import com.wakaztahir.codeeditor.theme.CodeTheme
import com.wakaztahir.codeeditor.theme.SyntaxColors
import com.wakaztahir.codeeditor.utils.parseCodeAsAnnotatedString
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.ui.ComposeActivity
import kotlinx.coroutines.launch

@ExperimentalMaterial3Api
class ConfigEditActivity : ComposeActivity() {

    companion object {
        const val EXTRA_CUSTOM_CONFIG = "custom_config"
    }

    private val viewModel by viewModels<ConfigEditActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialText = intent?.extras?.getString(EXTRA_CUSTOM_CONFIG) ?: "{}"

        setContent {
            AppTheme {
                ConfigEditScreen(
                    viewModel = viewModel,
                    onBackPress = { onBackPressedDispatcher.onBackPressed() },
                    initialText = initialText,
                    finish = { finish() },
                    saveAndExit = { text ->
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_CUSTOM_CONFIG, text))
                        finish()
                    },
                )
            }
        }
    }

}

@Composable
private fun ConfigEditScreen(
    modifier: Modifier = Modifier,
    viewModel: ConfigEditActivityViewModel,
    onBackPress: () -> Unit,
    initialText: String,
    finish: () -> Unit,
    saveAndExit: (text: String) -> Unit,
) {
    // Force LTR ( this is json editor + make AutoMirrored arrow correct  )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ConfigEditScreenContent(
            modifier = modifier,
            viewModel = viewModel,
            onBackPress = onBackPress,
            initialText = initialText,
            finish = finish,
            saveAndExit = saveAndExit,
        )
    }
}

@Composable
private fun ConfigEditScreenContent(
    modifier: Modifier = Modifier,
    viewModel: ConfigEditActivityViewModel,
    onBackPress: () -> Unit,
    initialText: String,
    finish: () -> Unit,
    saveAndExit: (text: String) -> Unit,
) {
    val context = LocalContext.current
    var alert by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.initialize(initialText)
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { uiEvent ->
            when (uiEvent) {
                is ConfigEditActivityUiEvent.Finish -> {
                    saveAndExit(uiEvent.text)
                }

                is ConfigEditActivityUiEvent.Alert -> alert = uiEvent.message

                is ConfigEditActivityUiEvent.SnackBar -> snackbarHostState.showSnackbar(
                    message = context.getString(uiEvent.id),
                    actionLabel = context.getString(android.R.string.ok),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    var showBackDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = uiState.textFieldValue.text != initialText) {
        showBackDialog = true
    }

    val coroutineScope = rememberCoroutineScope()
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val parser = remember { PrettifyParser() }

    val colorScheme = MaterialTheme.colorScheme
    val theme = remember(colorScheme) {
        object : CodeTheme(
            SyntaxColors(
                type = Color(0xFFA7E22E),
                keyword = Color(0xFFFA2772),
                literal = Color(0xFF66D9EE),
                comment = colorScheme.onSurfaceVariant,
                string = Color(0xFFE6DB74),
                punctuation = colorScheme.onSurface.copy(alpha = 0.7f),
                plain = colorScheme.onSurface,
                tag = Color(0xFFF92672),
                declaration = Color(0xFFFA2772),
                source = colorScheme.onSurface,
                attrName = Color(0xFFA6E22E),
                attrValue = Color(0xFFE6DB74),
                nocode = colorScheme.onSurface,
            ),
        ) {}
    }

    val highlightedText = remember(
        uiState.textFieldValue.text,
        theme,
    ) {
        parseCodeAsAnnotatedString(parser, theme, CodeLang.JSON, uiState.textFieldValue.text)
    }

    val displayValue = remember(
        highlightedText,
        uiState.textFieldValue.selection,
        uiState.textFieldValue.composition,
    ) {
        uiState.textFieldValue.copy(annotatedString = highlightedText)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_config)) },
                navigationIcon = {
                    SimpleIconButton(Icons.Filled.Close) {
                        onBackPress()
                    }
                },
                actions = {
                    SimpleIconButton(Icons.Filled.Done) {
                        viewModel.saveAndExit(uiState.textFieldValue.text)
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                windowInsets = WindowInsets.ime.union(
                    windowInsets.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SimpleIconButton(Icons.AutoMirrored.Filled.KeyboardTab) {
                            viewModel.insertText(" ".repeat(2))
                        }

                        val firstRowKeys = remember { listOf("{", "}", "[", "]") }
                        firstRowKeys.forEach { key ->
                            IconButton(onClick = {
                                viewModel.insertText(key)
                            }) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                )
                            }
                        }

                        SimpleIconButton(
                            Icons.AutoMirrored.Filled.Undo,
                            enabled = uiState.canUndo
                        ) {
                            viewModel.undo()
                        }

                        SimpleIconButton(
                            Icons.AutoMirrored.Filled.Redo,
                            enabled = uiState.canRedo
                        ) {
                            viewModel.redo()
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val secondRowKeys = remember { listOf("\"", ":", "-", "_") }
                        secondRowKeys.forEach { key ->
                            IconButton(onClick = {
                                viewModel.insertText(key)
                            }) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                )
                            }
                        }

                        SimpleIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft) {
                            viewModel.moveCursor(-1)
                        }

                        SimpleIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight) {
                            viewModel.moveCursor(1)
                        }

                        SimpleIconButton(Icons.AutoMirrored.Filled.FormatAlignLeft) {
                            viewModel.formatCurrentText()
                        }

                        SimpleIconButton(Icons.Filled.Info) {
                            coroutineScope.launch {
                                viewModel.checkConfig(uiState.textFieldValue.text)
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column {
            TextField(
                value = displayValue,
                onValueChange = { newValue ->
                    viewModel.onTextChange(
                        TextFieldValue(
                            text = newValue.text,
                            selection = newValue.selection,
                            composition = newValue.composition,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    if (alert != null) AlertDialog(
        onDismissRequest = {
            alert = null
        },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                alert = null
            }
        },
        icon = { Icon(Icons.Filled.Error, null) },
        title = { Text(stringResource(R.string.error_title)) },
        text = { Text(alert!!) },
    )

    if (showBackDialog) AlertDialog(
        onDismissRequest = {
            showBackDialog = false
        },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                viewModel.saveAndExit(uiState.textFieldValue.text)
            }
        },
        dismissButton = {
            TextButton(stringResource(R.string.no)) {
                finish()
            }
        },
        icon = { Icon(Icons.Filled.Warning, null) },
        title = { Text(stringResource(R.string.unsaved_changes_prompt)) },
    )
}

@Preview()
@Composable
private fun PreviewConfigEditScreen() {
    ConfigEditScreen(
        viewModel = ConfigEditActivityViewModel(),
        onBackPress = {},
        initialText = "{}",
        finish = {},
        saveAndExit = {},
    )
}