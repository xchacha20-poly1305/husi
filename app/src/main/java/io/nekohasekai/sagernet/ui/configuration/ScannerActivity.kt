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
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutScannerBinding
import io.nekohasekai.sagernet.ktx.forEachTry
import io.nekohasekai.sagernet.ktx.hasPermission
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.launch

class ScannerActivity : ThemedActivity() {

    private lateinit var binding: LayoutScannerBinding
    private val viewModel: ScannerActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 25) getSystemService<ShortcutManager>()!!.reportShortcutUsed("scan")

        binding = LayoutScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.add_profile_methods_scan_qr_code)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        binding.previewView.previewStreamState.observe(this) {
            if (it === PreviewView.StreamState.STREAMING) {
                binding.previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
        }
        if (hasPermission(Manifest.permission.CAMERA)) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.uiEvent.collect(::handleUiEvent)
            }
        }
    }

    private fun handleUiEvent(event: ScannerUiEvent) {
        when (event) {
            is ScannerUiEvent.ImportSubscription -> {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = event.uri
                })
            }

            is ScannerUiEvent.Snakebar -> snackbar(event.message).show()

            is ScannerUiEvent.SnakebarR -> snackbar(event.message).show()

            ScannerUiEvent.Finish -> finish()
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                setResult(RESULT_CANCELED)
                finish()
            }
        }

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraPreview: Preview
    private lateinit var camera: Camera

    private fun startCamera() {
        val cameraProviderFuture = try {
            ProcessCameraProvider.getInstance(this)
        } catch (e: Exception) {
            viewModel.onFailure(e)
            return
        }
        cameraProviderFuture.addListener({
            cameraProvider = try {
                cameraProviderFuture.get()
            } catch (e: Exception) {
                viewModel.onFailure(e)
                return@addListener
            }

            cameraPreview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            loadCamara()
        }, ContextCompat.getMainExecutor(this))
    }

    private val importCodeFile =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) {
            runOnDefaultDispatcher {
                try {
                    it.forEachTry { uri ->
                        viewModel.importFromUri(uri, contentResolver)
                    }
                } catch (e: Exception) {
                    viewModel.onFailure(e)
                }
            }
        }

    /**
     * See also: https://stackoverflow.com/a/31350642/2245107
     */
    override fun shouldUpRecreateTask(targetIntent: Intent?): Boolean {
        return super.shouldUpRecreateTask(targetIntent) || isTaskRoot
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return binding.previewView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.scanner_menu, menu)
        return true
    }

    private fun loadCamara() {
        val cameraSelector = if (viewModel.useFront) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                cameraPreview,
                viewModel.imageAnalysis,
            )

            flash?.isVisible = !viewModel.useFront
            lifecycleScope.launch {
                viewModel.isFlashlightOn.collect { enable ->
                    camera.cameraControl.enableTorch(enable)

                    updateFlashIcon(enable)
                }
            }
        } catch (e: Exception) {
            viewModel.onFailure(e)
        }
    }

    private var flash: MenuItem? = null
    private fun updateFlashIcon(enable: Boolean) {
        if (enable) {
            flash?.setIcon(R.drawable.ic_action_flight_off)
            flash?.setTitle(R.string.action_flash_off)
        } else {
            flash?.setIcon(R.drawable.ic_action_flight_on)
            flash?.setTitle(R.string.action_flash_on)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        flash = menu.findItem(R.id.action_flash)

        val isBackCamera = !viewModel.useFront
        flash?.isVisible = isBackCamera
        if (isBackCamera) {
            updateFlashIcon(viewModel.isFlashlightOn.value)
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_import_file -> {
                startFilesForResult(importCodeFile, "image/*")
            }

            R.id.action_flash -> {
                viewModel.toggleFlashlight()
            }

            // Switch front or back camera.
            R.id.action_camera_switch -> {
                viewModel.useFront = !viewModel.useFront
                viewModel.setFlashlight(false)
                loadCamara()
            }

            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG)
    }
}