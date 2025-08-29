package io.nekohasekai.sagernet.ui.tools

import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutToolsSpeedTestBinding
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.ui.getStringOrRes
import kotlinx.coroutines.launch

class SpeedtestActivity : ThemedActivity() {

    private lateinit var binding: LayoutToolsSpeedTestBinding
    private val viewModel by viewModels<SpeedTestActivityViewModel>()

    /** Skip updating view model value for setting text from program */
    private var changeFromProgram = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutToolsSpeedTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainLayout) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.speed_test)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        binding.speedTestMode.setOnCheckedChangeListener { _, checkedID ->
            when (checkedID) {
                R.id.speed_test_download -> {
                    viewModel.setMode(SpeedTestActivityViewModel.SpeedTestMode.Download)
                }

                R.id.speed_test_upload -> {
                    viewModel.setMode(SpeedTestActivityViewModel.SpeedTestMode.Upload)
                }
            }
        }

        binding.speedTestServer.addTextChangedListener {
            if (!changeFromProgram) {
                viewModel.setServer(it.toString())
            }
        }
        binding.speedTestTimeout.addTextChangedListener {
            if (!changeFromProgram) {
                viewModel.setTimeout(it.toString().toInt())
            }
        }
        binding.speedTestUploadSize.addTextChangedListener {
            if (!changeFromProgram) {
                viewModel.setUploadSize(it.toString().toLong())
            }
        }

        binding.speedTestMultipleSmallPacket.setOnCheckedChangeListener { _, isChecked ->
            if (!changeFromProgram) {
                viewModel.setMultiple(isChecked)
            }
        }
        binding.speedTest.setOnClickListener {
            viewModel.doSpeedTest()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleState)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect(::handleUiEvent)
            }
        }
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.mainLayout, text, Snackbar.LENGTH_LONG)
    }

    private fun handleState(state: SpeedTestActivityUiState) {
        changeFromProgram = true
        if (state.progress == null) {
            binding.speedTestProgress.isVisible = false
        } else {
            binding.speedTestProgress.isVisible = true
            binding.speedTestProgress.setProgressCompat(state.progress, true)
        }
        binding.speedTestResult.text = binding.speedTestResult.context.getString(
            R.string.speed,
            Formatter.formatFileSize(this, state.speed),
        )
        binding.speedTest.isClickable = state.canTest
        // If user is typing, this can prevent the focus backing to the head.
        val timeout = state.timeout.toString()
        if (timeout != binding.speedTestTimeout.text.toString()) {
            binding.speedTestTimeout.setText(state.timeout.toString())
        }
        when (state.mode) {
            SpeedTestActivityViewModel.SpeedTestMode.Download -> {
                binding.speedTestMode.check(R.id.speed_test_download)
                val url = if (state.downloadMultiple) {
                    state.multipleDownloadURL
                } else {
                    state.downloadURL
                }
                if (url != binding.speedTestServer.text.toString()) {
                    binding.speedTestServer.setText(state.downloadURL)
                }
                binding.speedTestUploadSizeLayout.isVisible = false
                binding.speedTestMultipleSmallPacket.isChecked = state.downloadMultiple
            }

            SpeedTestActivityViewModel.SpeedTestMode.Upload -> {
                binding.speedTestMode.check(R.id.speed_test_upload)
                val url = if (state.uploadMultiple) {
                    state.multipleUploadURL
                } else {
                    state.uploadURL
                }
                if (url != binding.speedTestServer.text.toString()) {
                    binding.speedTestServer.setText(state.uploadURL)
                }
                binding.speedTestUploadSizeLayout.isVisible = true
                val uploadLength = if (state.uploadMultiple) {
                    state.multipleUploadLength.toString()
                } else {
                    state.uploadLength.toString()
                }
                if (uploadLength != binding.speedTestUploadSize.text.toString()) {
                    binding.speedTestUploadSize.setText(state.uploadLength.toString())
                }
                binding.speedTestMultipleSmallPacket.isChecked = state.uploadMultiple
            }
        }
        changeFromProgram = false
    }

    private fun handleUiEvent(event: SpeedTestActivityUiEvent) {
        when (event) {
            is SpeedTestActivityUiEvent.ErrorAlert -> {
                alert(getStringOrRes(event.message))
            }

            is SpeedTestActivityUiEvent.Snackbar -> {
                snackbar(getStringOrRes(event.message)).show()
            }
        }
    }

}