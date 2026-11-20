package fr.husi.ui.configuration

import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.RectF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.camera2.interop.cameraCharacteristics
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.core.FocusState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.concurrent.futures.await
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.SwipeableSnackbarHost
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Surface
import fr.husi.compose.material3.Text
import fr.husi.compose.paddingExceptBottom
import fr.husi.compose.theme.AppTheme
import fr.husi.ktx.Logs
import fr.husi.ktx.forEachTry
import fr.husi.ktx.readableMessage
import fr.husi.ktx.toAndroidUri
import fr.husi.permission.AppPermission
import fr.husi.permission.LocalPermissionPlatform
import fr.husi.permission.rememberAndroidPermissionPlatform
import fr.husi.repository.Repository
import fr.husi.resources.Res
import fr.husi.resources.action_flash_off
import fr.husi.resources.action_flash_on
import fr.husi.resources.action_import_file
import fr.husi.resources.close
import fr.husi.resources.flashlight_off
import fr.husi.resources.flashlight_on
import fr.husi.resources.ok
import fr.husi.resources.photo
import fr.husi.resources.profile_import
import fr.husi.resources.question_mark
import fr.husi.resources.subscription_import
import fr.husi.ui.ComposeActivity
import fr.husi.ui.MainActivity
import fr.husi.ui.getStringOrRes
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class ScannerActivity : ComposeActivity() {

    companion object {
        const val EXTRA_FROM_SHORTCUT = "from_shortcut"

        private const val SHORTCUT_ID = "scan"
    }

    private val viewModel: ScannerActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getBooleanExtra(EXTRA_FROM_SHORTCUT, false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                getSystemService<ShortcutManager>()!!.reportShortcutUsed(SHORTCUT_ID)
            }
        }

        setContent {
            val platformPermission = rememberAndroidPermissionPlatform()
            CompositionLocalProvider(
                LocalPermissionPlatform provides platformPermission,
            ) {
                AppTheme {
                    ScannerScreen(
                        viewModel = viewModel,
                        onBackPress = { onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }
    }

    /**
     * See also: https://stackoverflow.com/a/31350642/2245107
     */
    override fun shouldUpRecreateTask(targetIntent: Intent?): Boolean {
        return super.shouldUpRecreateTask(targetIntent) || isTaskRoot
    }

}

@Composable
private fun ScannerScreen(
    modifier: Modifier = Modifier,
    viewModel: ScannerActivityViewModel,
    onBackPress: () -> Unit,
) {
    val repository = koinInject<Repository>()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permission = LocalPermissionPlatform.current

    var hasCameraPermission by remember(permission) {
        mutableStateOf(permission.hasPermission(AppPermission.Camera))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCameraPermission = permission.hasPermission(AppPermission.Camera)
    }
    LaunchedEffect(permission) {
        if (!hasCameraPermission) {
            permission.requestPermission(AppPermission.Camera) { granted ->
                hasCameraPermission = granted
            }
        }
    }

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var supportsTapToFocus by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAskDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context, lifecycleOwner, hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        val analysisExecutor = Dispatchers.Default.limitedParallelism(1).asExecutor()
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { req -> surfaceRequest = req }
        }
        val imageAnalysis = ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        imageAnalysis.setAnalyzer(
            analysisExecutor,
            ZxingQRCodeAnalyzer(viewModel::onSuccess, viewModel::onFailure),
        )

        try {
            val provider = ProcessCameraProvider.awaitInstance(context.applicationContext)
            try {
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
                val autofocusModes = camera.cameraInfo.cameraCharacteristics
                    .get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                supportsTapToFocus = autofocusModes?.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) == true
                viewModel.setHasFlashUnit(camera.cameraInfo.hasFlashUnit())

                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.uiState
                        .map { it.isFlashlightOn }
                        .distinctUntilChanged()
                        .collect { flashlightOn ->
                            camera.updateScannerLighting(flashlightOn)
                        }
                }
            } finally {
                imageAnalysis.clearAnalyzer()
                preview.surfaceProvider = null
                provider.unbind(preview, imageAnalysis)
                surfaceRequest = null
                supportsTapToFocus = false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.w("Failed to start the QR scanner camera", e)
            snackbarHostState.showSnackbar(e.readableMessage)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is ScannerUiEvent.ImportSubscription -> {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .setData(event.uri),
                    )
                }

                is ScannerUiEvent.Snakebar -> {
                    snackbarHostState.showSnackbar(
                        message = repository.getStringOrRes(event.message),
                        actionLabel = repository.getString(Res.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                is ScannerUiEvent.AskSubscriptionOrProfile -> {
                    showAskDialog = event.url
                }

                ScannerUiEvent.Finish -> {
                    onBackPress()
                }
            }
        }
    }

    val importCodeFile = rememberFilePickerLauncher(
        mode = FileKitMode.Multiple(),
        type = FileKitType.Image,
    ) { files ->
        if (files == null) return@rememberFilePickerLauncher
        scope.launch {
            try {
                files.forEachTry { file ->
                    val uri = file.toAndroidUri()
                    viewModel.importFromUri(uri, context.contentResolver)
                }
            } catch (e: Exception) {
                viewModel.onFailure(e)
            }
        }
    }

    val windowInsets = WindowInsets.safeDrawing
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SwipeableSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        var viewSize by remember { mutableStateOf(IntSize.Zero) }
        val scanBox = remember(viewSize) {
            defaultScanBox(viewSize)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .paddingExceptBottom(innerPadding)
                .onSizeChanged { viewSize = it },
        ) {
            surfaceRequest?.let { req ->
                var streaming by remember(req) { mutableStateOf(false) }
                var focusPosition by remember(req) { mutableStateOf(Offset.Zero) }
                var focusState by remember(req) { mutableIntStateOf(FocusState.INACTIVE) }
                var focusRequest by remember(req) { mutableIntStateOf(0) }
                CameraXViewfinder(
                    surfaceRequest = req,
                    modifier = Modifier.fillMaxSize(),
                    onStreamStateChanged = { state ->
                        streaming = state == Preview.STREAM_STATE_STREAMING
                        if (!streaming) {
                            focusState = FocusState.INACTIVE
                        }
                    },
                    isPinchToZoomEnabled = true,
                    isTapToFocusEnabled = supportsTapToFocus,
                    onTapToFocus = { position, state ->
                        if (state == FocusState.STARTED) {
                            focusRequest++
                        }
                        focusPosition = position
                        focusState = state
                    },
                )
                if (streaming) {
                    QrFinderOverlay(
                        box = scanBox,
                        lineThickness = 2.dp,
                    )
                    if (focusState != FocusState.INACTIVE) {
                        key(focusRequest) {
                            TapFocusIndicator(focusPosition, focusState)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                            .asPaddingValues(),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.size(36.dp),
                ) {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                        onClick = onBackPress,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.size(36.dp),
                ) {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.photo),
                        contentDescription = stringResource(Res.string.action_import_file),
                        onClick = { importCodeFile.launch() },
                    )
                }
            }


            if (surfaceRequest != null && uiState.hasFlashUnit) {
                val density = LocalDensity.current
                val flashButtonSize = 48.dp

                // Box to wrap offsets so that tooltip can be shown.
                Box(
                    modifier = Modifier
                        .size(flashButtonSize)
                        .offset {
                            val buttonSizePx = with(density) { flashButtonSize.toPx() }
                            IntOffset(
                                x = ((scanBox.left + scanBox.right - buttonSizePx) / 2).toInt(),
                                y = (scanBox.bottom - buttonSizePx - with(density) { 32.dp.toPx() }).toInt(),
                            )
                        },
                ) {
                    SimpleIconButton(
                        imageVector = if (uiState.isFlashlightOn) {
                            vectorResource(Res.drawable.flashlight_off)
                        } else {
                            vectorResource(Res.drawable.flashlight_on)
                        },
                        contentDescription = stringResource(
                            if (uiState.isFlashlightOn) {
                                Res.string.action_flash_off
                            } else {
                                Res.string.action_flash_on
                            },
                        ),
                        onClick = { viewModel.toggleFlashlight() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showAskDialog != null) {
        val url = showAskDialog!!
        AlertDialog(
            onDismissRequest = {
                showAskDialog = null
                viewModel.resetProcessing()
            },
            confirmButton = {
                TextButton(stringResource(Res.string.subscription_import)) {
                    viewModel.importSubscription(url)
                    showAskDialog = null
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.profile_import)) {
                    viewModel.parseAndImportProfile(url)
                    showAskDialog = null
                }
            },
            icon = {
                Icon(vectorResource(Res.drawable.question_mark), null)
            },
            title = { Text(stringResource(Res.string.profile_import)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(url)
                }
            },
        )
    }
}

private suspend fun Camera.updateScannerLighting(flashlightOn: Boolean) {
    try {
        if (cameraInfo.hasFlashUnit()) {
            cameraControl.enableTorch(flashlightOn).await()
        }
        if (!flashlightOn && cameraInfo.isLowLightBoostSupported) {
            cameraControl.enableLowLightBoostAsync(true).await()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: CameraControl.OperationCanceledException) {
        // The camera can close while a control request is pending.
    } catch (e: Exception) {
        Logs.w("Failed to set QR scanner lighting", e)
    }
}

private fun defaultScanBox(
    view: IntSize,
    boxWidthRatio: Float = 0.85f,
    boxHeightRatio: Float = 0.8f,
    verticalBias: Float = 0.33f,
): RectF {
    val boxW = (view.width * boxWidthRatio).roundToInt()
    val boxH = (view.height * boxHeightRatio).roundToInt()
    val left = (view.width - boxW) / 2f
    val top = (view.height - boxH) * verticalBias
    return RectF(left, top, left + boxW, top + boxH)
}

@Composable
private fun TapFocusIndicator(
    position: Offset,
    focusState: Int,
    modifier: Modifier = Modifier,
) {
    val visibleDurationMillis = 1000.milliseconds
    val fadeDurationMillis = 500
    val opacity = remember { Animatable(1f) }
    LaunchedEffect(focusState) {
        if (focusState != FocusState.STARTED) {
            delay(visibleDurationMillis)
            opacity.animateTo(0f, tween(fadeDurationMillis))
        }
    }
    val color = when (focusState) {
        FocusState.FOCUSED -> MaterialTheme.colorScheme.primary
        FocusState.FAILED, FocusState.NOT_FOCUSED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Canvas(modifier.fillMaxSize()) {
        val alpha = opacity.value
        if (alpha <= 0f) return@Canvas

        val halfSize = 24.dp.toPx()
        val cornerLength = 12.dp.toPx()
        val corners = Path()
        for (horizontal in -1..1 step 2) {
            for (vertical in -1..1 step 2) {
                val x = position.x + horizontal * halfSize
                val y = position.y + vertical * halfSize
                corners.moveTo(x - horizontal * cornerLength, y)
                corners.lineTo(x, y)
                corners.lineTo(x, y - vertical * cornerLength)
            }
        }
        drawPath(corners, color, alpha = alpha, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun QrFinderOverlay(
    box: RectF,
    modifier: Modifier = Modifier,
    lineThickness: Dp = 2.dp,
) {
    val lineColor = MaterialTheme.colorScheme.primary

    val density = LocalDensity.current
    val linePx = with(density) { lineThickness.toPx() }

    val transition = rememberInfiniteTransition(label = "qr-line")
    val animY by transition.animateFloat(
        initialValue = 0f,
        targetValue = box.height(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "qr-line-y",
    )

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        val y = box.top + animY
        val startX = box.left + 8f
        val endX = box.right - 8f

        val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
            0f to lineColor.copy(alpha = 0f),
            0.3f to lineColor.copy(alpha = 0.6f),
            0.5f to lineColor,
            0.7f to lineColor.copy(alpha = 0.6f),
            1f to lineColor.copy(alpha = 0f),
            startX = startX,
            endX = endX,
        )

        drawLine(
            brush = brush,
            start = Offset(startX, y),
            end = Offset(endX, y),
            strokeWidth = linePx * 2,
            pathEffect = PathEffect.cornerPathEffect(8f),
        )
    }
}
