/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/


package io.nekohasekai.sagernet.ui.configuration

import android.Manifest
import android.content.Intent
import android.content.pm.ShortcutManager
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.paddingExceptBottom
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.ktx.forEachTry
import io.nekohasekai.sagernet.ui.ComposeActivity
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.getStringOrRes
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@ExperimentalMaterial3Api
class ScannerActivity : ComposeActivity() {

    companion object {
        const val SHORTCUT_ID = "scan"
    }

    private val viewModel: ScannerActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            getSystemService<ShortcutManager>()!!.reportShortcutUsed(SHORTCUT_ID)
        }

        setContent {
            AppTheme {
                ScannerScreen(
                    viewModel = viewModel,
                    onBackPress = { onBackPressedDispatcher.onBackPressed() },
                )
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ScannerScreen(
    modifier: Modifier = Modifier,
    viewModel: ScannerActivityViewModel,
    onBackPress: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val camaraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!camaraPermission.status.isGranted) {
            camaraPermission.launchPermissionRequest()
        }
    }

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        provider.unbindAll()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { req -> surfaceRequest = req }
        }

        val imageAnalysis = ImageAnalysis.Builder().build().apply {
            setAnalyzer(
                analysisExecutor,
                ZxingQRCodeAnalyzer(viewModel::onSuccess, viewModel::onFailure),
            )
        }

        val camera = provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis,
        )

        val hasFlash = camera.cameraInfo.hasFlashUnit()
        viewModel.setHasFlashUnit(hasFlash)

        launch {
            viewModel.uiState.collect { state ->
                camera.cameraControl.enableTorch(state.isFlashlightOn)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is ScannerUiEvent.ImportSubscription -> {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .setData(event.uri)
                    )
                }

                is ScannerUiEvent.Snakebar -> {
                    snackbarHostState.showSnackbar(
                        message = context.getStringOrRes(event.message),
                        actionLabel = context.getString(android.R.string.ok),
                        duration = SnackbarDuration.Short,
                    )
                }

                ScannerUiEvent.Finish -> {
                    onBackPress()
                }
            }
        }
    }

    val importCodeFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        scope.launch {
            try {
                uris.forEachTry { uri ->
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                CameraXViewfinder(
                    surfaceRequest = req,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            QrFinderOverlay(
                box = scanBox,
                lineThickness = 2.dp,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                            .asPaddingValues()
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
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                        onClick = onBackPress,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.size(36.dp),
                ) {
                    SimpleIconButton(
                        imageVector = Icons.Filled.Photo,
                        contentDescription = stringResource(R.string.action_import_file),
                        onClick = { importCodeFile.launch("image/*") },
                    )
                }
            }


            if (uiState.hasFlashUnit) {
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
                            Icons.Filled.FlashlightOff
                        } else {
                            Icons.Filled.FlashlightOn
                        },
                        contentDescription = stringResource(
                            if (uiState.isFlashlightOn) {
                                R.string.action_flash_off
                            } else {
                                R.string.action_flash_on
                            }
                        ),
                        onClick = { viewModel.toggleFlashlight() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
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
        modifier = modifier.fillMaxSize()
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