package io.nekohasekai.sagernet.ui.dashboard

import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.databinding.LayoutDashboardConnectionBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColour
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.OnKeyDownFragment
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import io.nekohasekai.sagernet.compose.SimpleIconButton
import libcore.Libcore

@ExperimentalMaterial3Api
class ConnectionFragment() :
    OnKeyDownFragment(R.layout.layout_dashboard_connection) {

    companion object {
        private const val ARG_CONN = "conn"
    }

    constructor(conn: Connection) : this() {
        arguments = bundleOf(
            ARG_CONN to conn
        )
    }

    private lateinit var binding: LayoutDashboardConnectionBinding
    private val viewModel by viewModels<ConnectionFragmentViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val conn =
            BundleCompat.getParcelable(requireArguments(), ARG_CONN, Connection::class.java)!!
        viewModel.initialize(conn) {
            (requireActivity() as MainActivity).connection.service
                ?.queryConnections(Libcore.ShowTrackerActively)
                ?.connections
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutDashboardConnectionBinding.bind(view)
        binding.toolbar.setContent {
            @Suppress("DEPRECATION")
            Mdc3Theme {
                TopAppBar(
                    title = { Text(viewModel.uiState.collectAsState().value.connection.uuid) },
                    navigationIcon = {
                        SimpleIconButton(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        ) {
                            (requireActivity() as MainActivity).onBackPressedCallback.isEnabled = true
                            parentFragmentManager.popBackStack()
                        }
                    },
                    actions = {
                        SimpleIconButton(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = stringResource(R.string.close),
                        ) {
                            val activity = requireActivity() as MainActivity
                            activity.connection.service?.closeConnection(viewModel.uiState.value.connection.uuid)
                            activity.onBackPressedCallback.isEnabled = true
                            activity.supportFragmentManager.popBackStack()
                        }
                    },
                )
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom + dp2px(64),
            )
            insets
        }

        (requireActivity() as MainActivity).onBackPressedCallback.isEnabled = false

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }
    }

    override fun onDestroy() {
        viewModel.stop()
        super.onDestroy()
    }

    private fun handleUiState(state: ConnectionFragmentState) {
        val conn = state.connection
        if (conn.closed) {
            binding.connStatus.setText(R.string.connection_status_closed)
            binding.connStatus.setTextColor(binding.connStatus.context.getColour(R.color.material_red_900))
        } else {
            binding.connStatus.setText(R.string.connection_status_active)
            binding.connStatus.setTextColor(binding.connStatus.context.getColour(R.color.material_green_500))
        }
        binding.connNetwork.text = conn.network.uppercase()
        if (conn.protocol != null) {
            binding.connProtocolCard.isVisible = true
            binding.connProtocol.text = conn.protocol.uppercase()
        } else {
            binding.connProtocolCard.isVisible = false
        }
        binding.connTime.text = conn.start
        if (conn.ipVersion != null) {
            binding.connIPVersionCard.isVisible = true
            binding.connIPVersion.text = conn.ipVersion.toString()
        } else {
            binding.connIPVersionCard.isVisible = false
        }
        binding.connUpload.text =
            Formatter.formatFileSize(binding.connUpload.context, conn.uploadTotal)
        binding.connDownload.text =
            Formatter.formatFileSize(binding.connDownload.context, conn.downloadTotal)
        binding.connInbound.text = conn.inbound
        binding.connSource.text = conn.src
        binding.connDestination.text = conn.dst
        if (conn.host.isNotBlank()) {
            binding.connHostCard.isVisible = true
            binding.connHost.text = conn.host
        } else {
            binding.connHostCard.isVisible = false
        }
        binding.connRule.text = conn.matchedRule
        binding.connOutbound.text = conn.outbound
        binding.connChain.text = conn.chain
        if (conn.process.isNullOrBlank()) {
            binding.connProcessCard.isVisible = false
        } else {
            binding.connProcessCard.isVisible = true
            binding.connProcess.text = conn.process
        }
    }

}