package io.nekohasekai.sagernet.ui.tools

import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SPEED_TEST_URL
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutSpeedTestBinding
import io.nekohasekai.sagernet.ktx.alertAndLog
import io.nekohasekai.sagernet.ui.ThemedActivity

class SpeedtestActivity : ThemedActivity() {

    private lateinit var binding: LayoutSpeedTestBinding
    private val viewModel: SpeedTestActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutSpeedTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = bars.top,
                left = bars.left,
                right = bars.right,
            )
            insets
        }
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

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.speed_test)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        binding.speedTestServer.setText(DataStore.speedTestUrl.ifBlank {
            SPEED_TEST_URL
        })
        binding.speedTestTimeout.setText(DataStore.speedTestTimeout.toString())

        binding.speedTest.setOnClickListener {
            viewModel.doTest(
                binding.speedTestServer.text.toString(),
                binding.speedTestTimeout.text.toString().toInt(),
            )
        }

        viewModel.uiState.observe(this, ::handleState)
    }

    override fun onDestroy() {
        DataStore.speedTestUrl = binding.speedTestServer.text.toString()
        DataStore.speedTestTimeout = binding.speedTestTimeout.text.toString().toInt()
        super.onDestroy()
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.mainLayout, text, Snackbar.LENGTH_LONG)
    }

    private fun handleState(state: SpeedTestActivityUiState) {
        when (state) {
            SpeedTestActivityUiState.Idle -> {
                binding.speedTest.isEnabled = true
            }

            is SpeedTestActivityUiState.Doing -> {
                binding.speedTest.isEnabled = false

                val speed = Formatter.formatFileSize(this, state.result)
                val text = getString(R.string.speed, speed)
                binding.speedTestResult.text = text
            }

            is SpeedTestActivityUiState.Done -> {
                binding.speedTest.isEnabled = true
                snackbar(R.string.done).show()
                state.exception?.let { e ->
                    alertAndLog(e).show()
                }
            }
        }
    }
}