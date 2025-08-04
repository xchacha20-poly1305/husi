package io.nekohasekai.sagernet.ui.tools

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.databinding.LayoutStunBinding
import io.nekohasekai.sagernet.ktx.currentSocks5
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.launch

class StunActivity : ThemedActivity() {

    private lateinit var binding: LayoutStunBinding
    private val viewModel: StunActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutStunBinding.inflate(layoutInflater)
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
            title = getString(R.string.stun_test)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        currentSocks5()?.string?.let {
            binding.proxyServer.setText(it)
        }

        binding.stunTest.setOnClickListener {
            SagerNet.inputMethod.hideSoftInputFromWindow(binding.root.windowToken, 0)
            viewModel.doTest(
                binding.natStunServer.text.toString(),
                binding.proxyServer.text.toString(),
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }
    }

    private fun handleUiState(state: StunUiState) {
        when (state) {
            is StunUiState.Idle -> {
                binding.waitLayout.isVisible = false
                binding.stunTest.isEnabled = true
            }

            is StunUiState.Doing -> {
                binding.waitLayout.isVisible = true
                binding.stunTest.isEnabled = false
            }

            is StunUiState.Done -> {
                binding.waitLayout.isVisible = false
                binding.stunTest.isEnabled = true
                binding.natResult.text = state.result
            }
        }
    }
}