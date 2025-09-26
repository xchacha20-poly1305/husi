package io.nekohasekai.sagernet.ui.tools

import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleTopAppBar
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.databinding.LayoutToolsSpeedTestBinding
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.textChanges
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.ui.getStringOrRes
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SpeedtestActivity : ThemedActivity() {

    companion object {
        private const val DEBOUNCE_DURATION = 500L
    }

    private lateinit var binding: LayoutToolsSpeedTestBinding
    private val viewModel by viewModels<SpeedTestActivityViewModel>()

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

        binding.toolbar.setContent {
            @Suppress("DEPRECATION")
            AppTheme {
                SimpleTopAppBar(
                    title = R.string.show_direct_speed_sum,
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                ) {
                    onBackPressedDispatcher.onBackPressed()
                }
            }
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

        binding.speedTestServer
            .textChanges()
            .debounce(DEBOUNCE_DURATION)
            .filter { editable ->
                val currentServerUrl = when (viewModel.uiState.value.mode) {
                    SpeedTestActivityViewModel.SpeedTestMode.Download -> viewModel.uiState.value.downloadURL
                    SpeedTestActivityViewModel.SpeedTestMode.Upload -> viewModel.uiState.value.uploadURL
                }
                editable?.toString() != currentServerUrl
            }
            .onEach { editable ->
                viewModel.setServer(editable?.toString())
            }
            .launchIn(lifecycleScope)
        binding.speedTestTimeout
            .textChanges()
            .debounce(DEBOUNCE_DURATION)
            .filter { editable ->
                editable?.toString() != viewModel.uiState.value.timeout.toString()
            }
            .onEach { editable ->
                viewModel.setTimeout(editable?.toString())
            }
            .launchIn(lifecycleScope)
        binding.speedTestUploadSize
            .textChanges()
            .debounce(DEBOUNCE_DURATION)
            .filter { editable ->
                editable?.toString() != viewModel.uiState.value.uploadLength.toString()
            }
            .onEach { editable ->
                viewModel.setUploadSize(editable?.toString())
            }
            .launchIn(lifecycleScope)

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
        if (state.timeoutError == null) {
            binding.speedTestTimeoutLayout.isErrorEnabled = false
            // If user is typing, this can prevent the focus backing to the head.
            val timeout = state.timeout.toString()
            if (timeout != binding.speedTestTimeout.text.toString()) {
                binding.speedTestTimeout.setText(state.timeout.toString())
            }
        } else {
            binding.speedTestTimeoutLayout.apply {
                isErrorEnabled = true
                error = context.getStringOrRes(state.timeoutError)
            }
        }
        when (state.mode) {
            SpeedTestActivityViewModel.SpeedTestMode.Download -> {
                binding.speedTestMode.check(R.id.speed_test_download)
                if (state.urlError == null) {
                    binding.speedTestServerLayout.isErrorEnabled = false
                    if (state.downloadURL != binding.speedTestServer.text.toString()) {
                        binding.speedTestServer.setText(state.downloadURL)
                    }
                } else {
                    binding.speedTestServerLayout.apply {
                        isErrorEnabled = true
                        error = context.getStringOrRes(state.urlError)
                    }
                }
                binding.speedTestUploadSizeLayout.isVisible = false
            }

            SpeedTestActivityViewModel.SpeedTestMode.Upload -> {
                binding.speedTestMode.check(R.id.speed_test_upload)
                if (state.urlError == null) {
                    if (state.uploadURL != binding.speedTestServer.text.toString()) {
                        binding.speedTestServer.setText(state.uploadURL)
                    }
                } else {
                    binding.speedTestServerLayout.apply {
                        isErrorEnabled = true
                        error = context.getStringOrRes(state.urlError)
                    }
                }
                binding.speedTestUploadSizeLayout.isVisible = true
                if (state.uploadLengthError == null) {
                    binding.speedTestUploadSizeLayout.isErrorEnabled = false
                    val uploadLength = state.uploadLength.toString()
                    if (uploadLength != binding.speedTestUploadSize.text.toString()) {
                        binding.speedTestUploadSize.setText(state.uploadLength.toString())
                    }
                } else {
                    binding.speedTestUploadSizeLayout.apply {
                        isErrorEnabled = true
                        error = context.getStringOrRes(state.uploadLengthError)
                    }
                }
            }
        }
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