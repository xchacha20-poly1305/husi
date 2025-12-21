@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.nekohasekai.sagernet.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wakaztahir.codeeditor.model.CodeLang
import com.wakaztahir.codeeditor.prettify.PrettifyParser
import com.wakaztahir.codeeditor.theme.CodeTheme
import com.wakaztahir.codeeditor.theme.SyntaxColors
import com.wakaztahir.codeeditor.utils.parseCodeAsAnnotatedString
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.MoreOverIcon
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.TextButton
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.ui.ComposeActivity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
        if (savedInstanceState == null) {
            viewModel.initialize(initialText)
        }

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
private fun RepeatableIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    initialDelay: Long = 500L,
    repeatDelay: Long = 60L,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) { } // Make ripple
            .pointerInput(enabled) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        val job = launch {
                            onClick()
                            delay(initialDelay)
                            while (true) {
                                onClick()
                                delay(repeatDelay)
                            }
                        }
                        waitForUpOrCancellation()
                        job.cancel()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (enabled) {
                LocalContentColor.current
            } else {
                LocalContentColor.current.copy(alpha = 0.38f)
            },
        )
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
    val currentText = viewModel.textFieldState.text

    var showBackDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = currentText.toString() != initialText) {
        showBackDialog = true
    }

    LaunchedEffect(currentText) {
        viewModel.onTextChange()
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

    val outputTransformation = remember(parser, theme) {
        OutputTransformation {
            val text = asCharSequence().toString()
            val highlighted = parseCodeAsAnnotatedString(parser, theme, CodeLang.JSON, text)
            highlighted.spanStyles.forEach { spanStyle ->
                addStyle(spanStyle.item, spanStyle.start, spanStyle.end)
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_config)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close),
                    ) {
                        onBackPress()
                    }
                },
                actions = {
                    AppBarRow(
                        overflowIndicator = ::MoreOverIcon,
                    ) {
                        clickableItem(
                            onClick = { viewModel.saveAndExit(currentText.toString()) },
                            icon = {
                                Icon(ImageVector.vectorResource(R.drawable.done), null)
                            },
                            label = context.getString(R.string.apply),
                        )
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding),
        ) {
            val density = LocalDensity.current
            val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val navigationPadding =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val toolbarYOffset = ScreenOffset + imePadding + navigationPadding
            var toolbarHeightPx by remember { mutableIntStateOf(0) }
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = -toolbarYOffset)
                    .zIndex(1f)
                    .onSizeChanged { toolbarHeightPx = it.height },
                // Fake liquid glass
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                        alpha = 0.9f,
                    ),
                    toolbarContentColor = MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.8f,
                    ),
                ),
                scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
                    FloatingToolbarExitDirection.Bottom,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.keyboard_tab),
                        contentDescription = stringResource(R.string.indent),
                        onClick = {
                            // https://github.com/SagerNet/sing-box/blob/43a3beb98851ad5e27e60042ea353b63c7d77448/experimental/libbox/config.go#L169
                            viewModel.insertText(" ".repeat(2))
                        },
                    )
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.undo),
                        contentDescription = stringResource(R.string.undo),
                        enabled = uiState.canUndo,
                        onClick = viewModel::undo,
                    )
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.redo),
                        contentDescription = stringResource(R.string.redo),
                        enabled = uiState.canRedo,
                        onClick = viewModel::redo,
                    )
                    RepeatableIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.keyboard_arrow_left),
                        contentDescription = "<-",
                        onClick = { viewModel.moveCursor(-1) },
                    )

                    RepeatableIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.keyboard_arrow_right),
                        contentDescription = "->",
                        onClick = { viewModel.moveCursor(1) },
                    )

                    val keys = listOf("{", "}", "[", "]", ":", "\"", "_", "-")
                    keys.forEach { key ->
                        IconButton(
                            onClick = {
                                viewModel.insertText(key)
                            },
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }

                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.format_align_left),
                        contentDescription = stringResource(R.string.action_format),
                        onClick = { viewModel.formatCurrentText() },
                    )
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.info),
                        contentDescription = stringResource(R.string.action_test_config),
                        onClick = {
                            coroutineScope.launch {
                                viewModel.checkConfig(currentText.toString())
                            }
                        },
                    )
                }
            }
            val extraHeight = with(density) { toolbarHeightPx.toDp() } + toolbarYOffset
            val focusRequester = remember { FocusRequester() }
            val verticalScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(verticalScrollState)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { focusRequester.requestFocus() },
            ) {
                BasicTextField(
                    state = viewModel.textFieldState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    lineLimits = TextFieldLineLimits.MultiLine(),
                    outputTransformation = outputTransformation,
                )
                Spacer(modifier = Modifier.height(extraHeight))
            }
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
        icon = { Icon(ImageVector.vectorResource(R.drawable.error), null) },
        title = { Text(stringResource(R.string.error_title)) },
        text = { Text(alert!!) },
    )

    if (showBackDialog) AlertDialog(
        onDismissRequest = {
            showBackDialog = false
        },
        confirmButton = {
            TextButton(stringResource(android.R.string.ok)) {
                viewModel.saveAndExit(currentText.toString())
            }
        },
        dismissButton = {
            TextButton(stringResource(R.string.no)) {
                finish()
            }
        },
        icon = { Icon(ImageVector.vectorResource(R.drawable.warning), null) },
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