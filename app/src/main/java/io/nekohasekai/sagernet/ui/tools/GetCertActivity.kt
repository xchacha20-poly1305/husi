package io.nekohasekai.sagernet.ui.tools

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.databinding.LayoutGetCertBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.alertAndLog
import io.nekohasekai.sagernet.ktx.currentSocks5
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.launch

class GetCertActivity : ThemedActivity() {

    private lateinit var binding: LayoutGetCertBinding
    private val viewModel: GetCertActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutGetCertBinding.inflate(layoutInflater)
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
            setTitle(R.string.get_cert)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        currentSocks5()?.string?.let {
            binding.proxyServer.setText(it)
        }

        val protocols = resources.getStringArray(R.array.naive_proto_entry)
        binding.pinCertProtocol.setAdapter(
            ArrayAdapter(
                binding.pinCertProtocol.context,
                android.R.layout.simple_list_item_1,
                protocols,
            )
        )
        binding.pinCertProtocol.setText(protocols[0], false)

        binding.getCert.setOnClickListener {
            SagerNet.inputMethod.hideSoftInputFromWindow(binding.root.windowToken, 0)

            val server = binding.pinCertServer.text.toString()
            val serverName = binding.pinCertServerName.text.toString()
            val protocol = binding.pinCertProtocol.text.toString()
            val proxy = binding.proxyServer.text.toString()
            viewModel.getCert(server, serverName, protocol, proxy)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }
    }

    private fun handleUiState(state: GetCertUiState) {
        when (state) {
            GetCertUiState.Idle -> {
                binding.waitLayout.isVisible = false
            }

            GetCertUiState.Doing -> {
                binding.waitLayout.isVisible = true
            }

            is GetCertUiState.Done -> {
                binding.waitLayout.isVisible = false

                Logs.i(state.cert)
                SagerNet.trySetPrimaryClip(state.cert)
                Snackbar.make(
                    binding.root,
                    R.string.get_cert_success,
                    Snackbar.LENGTH_SHORT,
                ).show()
            }

            is GetCertUiState.Failure -> {
                binding.waitLayout.isVisible = false

                alertAndLog(state.exception).show()
            }
        }
    }
}
