@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.husi.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakaztahir.codeeditor.model.CodeLang
import com.wakaztahir.codeeditor.prettify.PrettifyParser
import com.wakaztahir.codeeditor.theme.CodeTheme
import com.wakaztahir.codeeditor.theme.SyntaxColors
import com.wakaztahir.codeeditor.utils.parseCodeAsAnnotatedString
import fr.husi.compose.BackHandler
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.CapsuleActionButton
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.TooltipIconButton
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.IconButton
import fr.husi.compose.material3.Text
import fr.husi.keyevent.isTypeControlPressed
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.action_format
import fr.husi.resources.action_test_config
import fr.husi.resources.apply
import fr.husi.resources.close
import fr.husi.resources.done
import fr.husi.resources.edit_config
import fr.husi.resources.error
import fr.husi.resources.error_title
import fr.husi.resources.format_align_left
import fr.husi.resources.indent
import fr.husi.resources.info
import fr.husi.resources.keyboard_arrow_left
import fr.husi.resources.keyboard_arrow_right
import fr.husi.resources.keyboard_tab
import fr.husi.resources.no
import fr.husi.resources.ok
import fr.husi.resources.redo
import fr.husi.resources.undo
import fr.husi.resources.unsaved_changes_prompt
import fr.husi.resources.warning
import fr.husi.results.LocalResultEventBus
import fr.husi.results.ResultEventBus
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.random.Random

@Composable
private fun RepeatableIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    contentColor: Color = LocalContentColor.current,
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
                contentColor
            } else {
                contentColor.copy(alpha = 0.38f)
            },
        )
    }
}

@Composable
private fun BoxScope.ConfigEditFloatingToolbar(
    viewModel: ConfigEditViewModel,
    uiState: ConfigEditUiState,
    coroutineScope: CoroutineScope,
    toolbarYOffset: Dp,
    onHeightChanged: (Int) -> Unit,
) {
    val toolbarButtonContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val toolbarIconButtonColors = IconButtonDefaults.iconButtonColors(
        contentColor = toolbarButtonContentColor,
    )
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .offset(y = -toolbarYOffset)
            .zIndex(1f)
            .onSizeChanged { onHeightChanged(it.height) },
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = 0.9f,
            ),
            toolbarContentColor = MaterialTheme.colorScheme.primary.copy(
                alpha = 0.8f,
            ),
        ),
    ) {
        val toolbarScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .then(
                    if (useCompactConfigEditFloatingToolbar) {
                        Modifier
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
                .fadingEdge(
                    scrollableState = toolbarScrollState,
                    orientation = Orientation.Horizontal,
                    fadeStart = true,
                    fadeEnd = true,
                )
                .horizontalScroll(toolbarScrollState),
        ) {
            if (showFloatingToolbarSymbolAndCursorButtons) {
                TooltipIconButton(
                    icon = vectorResource(Res.drawable.keyboard_tab),
                    contentDescription = stringResource(Res.string.indent),
                    colors = toolbarIconButtonColors,
                    onClick = {
                        // https://github.com/SagerNet/sing-box/blob/43a3beb98851ad5e27e60042ea353b63c7d77448/experimental/libbox/config.go#L169
                        viewModel.insertText(" ".repeat(2))
                    },
                )
            }
            TooltipIconButton(
                icon = vectorResource(Res.drawable.undo),
                contentDescription = stringResource(Res.string.undo),
                enabled = uiState.canUndo,
                colors = toolbarIconButtonColors,
                onClick = viewModel::undo,
            )
            TooltipIconButton(
                icon = vectorResource(Res.drawable.redo),
                contentDescription = stringResource(Res.string.redo),
                enabled = uiState.canRedo,
                colors = toolbarIconButtonColors,
                onClick = viewModel::redo,
            )
            if (showFloatingToolbarSymbolAndCursorButtons) {
                RepeatableIconButton(
                    imageVector = vectorResource(Res.drawable.keyboard_arrow_left),
                    contentDescription = "<-",
                    contentColor = toolbarButtonContentColor,
                    onClick = { viewModel.moveCursor(-1) },
                )

                RepeatableIconButton(
                    imageVector = vectorResource(Res.drawable.keyboard_arrow_right),
                    contentDescription = "->",
                    contentColor = toolbarButtonContentColor,
                    onClick = { viewModel.moveCursor(1) },
                )

                val keys = listOf("{", "}", "[", "]", ":", "\"", "_", "-")
                keys.forEach { key ->
                    IconButton(
                        onClick = {
                            viewModel.insertText(key)
                        },
                        contentColor = toolbarButtonContentColor,
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }

            TooltipIconButton(
                icon = vectorResource(Res.drawable.format_align_left),
                contentDescription = stringResource(Res.string.action_format),
                colors = toolbarIconButtonColors,
                onClick = { viewModel.formatCurrentText() },
            )
            TooltipIconButton(
                icon = vectorResource(Res.drawable.info),
                contentDescription = stringResource(Res.string.action_test_config),
                colors = toolbarIconButtonColors,
                onClick = {
                    coroutineScope.launch {
                        viewModel.checkConfig()
                    }
                },
            )
        }
    }
}

/**
 * Result definition: String? ( null means user cancel, otherwise is the edited config )
 */
@Composable
fun ConfigEditScreen(
    modifier: Modifier = Modifier,
    initialText: String,
    resultKey: String,
    onBack: () -> Unit,
) {
    val viewModel: ConfigEditViewModel = viewModel {
        ConfigEditViewModel(initialText)
    }
    // Force LTR ( this is JSON editor + make AutoMirrored arrow correct  )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ConfigEditScreenContent(
            modifier = modifier,
            viewModel = viewModel,
            initialText = initialText,
            resultKey = resultKey,
            onBack = onBack,
        )
    }
}

@Composable
private fun ConfigEditScreenContent(
    modifier: Modifier = Modifier,
    viewModel: ConfigEditViewModel,
    initialText: String,
    resultKey: String,
    onBack: () -> Unit,
) {
    val resultBus = LocalResultEventBus.current
    var alert by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { uiEvent ->
            when (uiEvent) {
                is ConfigEditUiEvent.Finish -> {
                    resultBus.sendResult<String?>(resultKey, uiEvent.text)
                    onBack()
                }

                is ConfigEditUiEvent.Alert -> alert = uiEvent.message

                is ConfigEditUiEvent.SnackBar -> snackbarHostState.showSnackbar(
                    message = resolveRepository().getString(uiEvent.id),
                    actionLabel = resolveRepository().getString(Res.string.ok),
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    var showBackDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        if (viewModel.textFieldState.text.toString() != initialText) {
            showBackDialog = true
        } else {
            resultBus.sendResult<String?>(resultKey, null)
            onBack()
        }
    }

    LaunchedEffect(viewModel) {
        snapshotFlow { viewModel.textFieldState.text.toString() }.collect { text ->
            viewModel.onTextChange(text)
        }
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
            CapsuleTopBar(
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                    ) {
                        resultBus.sendResult<String?>(resultKey, null)
                        onBack()
                    }
                },
                title = { Text(stringResource(Res.string.edit_config)) },
                actions = {
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.done),
                            contentDescription = stringResource(Res.string.apply),
                        ) {
                            viewModel.saveAndExit()
                        }
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            val density = LocalDensity.current
            val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val navigationPadding =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val toolbarYOffset = ScreenOffset + maxOf(imePadding, navigationPadding)
            var toolbarHeightPx by remember { mutableIntStateOf(0) }
            ConfigEditFloatingToolbar(
                viewModel = viewModel,
                uiState = uiState,
                coroutineScope = coroutineScope,
                toolbarYOffset = toolbarYOffset,
                onHeightChanged = { toolbarHeightPx = it },
            )
            val extraHeight = with(density) { toolbarHeightPx.toDp() } + toolbarYOffset
            var editorHeightPx by remember { mutableIntStateOf(0) }
            val editorMinHeight = (
                    with(density) { editorHeightPx.toDp() } - extraHeight
                    ).coerceAtLeast(0.dp)
            val focusRequester = remember { FocusRequester() }
            val verticalScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { editorHeightPx = it.height },
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(verticalScrollState)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { focusRequester.requestFocus() },
                ) {
                    Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding()))
                    BasicTextField(
                        state = viewModel.textFieldState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = editorMinHeight)
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) {
                                    return@onPreviewKeyEvent false
                                }
                                if (!keyEvent.isTypeControlPressed || keyEvent.key != Key.Z) {
                                    return@onPreviewKeyEvent false
                                }
                                if (keyEvent.isShiftPressed) {
                                    viewModel.redo()
                                } else {
                                    viewModel.undo()
                                }
                                true
                            },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        lineLimits = TextFieldLineLimits.MultiLine(),
                        outputTransformation = outputTransformation,
                    )
                    Spacer(modifier = Modifier.height(extraHeight))
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier.fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = verticalScrollState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        }
    }

    if (alert != null) AlertDialog(
        onDismissRequest = {
            alert = null
        },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                alert = null
            }
        },
        icon = { Icon(vectorResource(Res.drawable.error), null) },
        title = { Text(stringResource(Res.string.error_title)) },
        text = { Text(alert!!) },
    )

    if (showBackDialog) AlertDialog(
        onDismissRequest = {
            showBackDialog = false
        },
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) {
                viewModel.saveAndExit()
            }
        },
        dismissButton = {
            TextButton(stringResource(Res.string.no)) {
                resultBus.sendResult<String?>(resultKey, null)
                onBack()
            }
        },
        icon = { Icon(vectorResource(Res.drawable.warning), null) },
        title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
    )
}

@Preview
@Composable
private fun PreviewConfigEditScreen() {
    CompositionLocalProvider(
        LocalResultEventBus provides remember { ResultEventBus() },
    ) {
        ConfigEditScreen(
            initialText = "{}",
            resultKey = "config-edit-${Random.nextLong()}",
            onBack = {},
        )
    }
}
