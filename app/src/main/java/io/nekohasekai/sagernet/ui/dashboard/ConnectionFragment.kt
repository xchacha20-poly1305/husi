package io.nekohasekai.sagernet.ui.dashboard

import android.os.Bundle
import android.text.format.Formatter
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.databinding.LayoutDashboardConnectionBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColour
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ToolbarFragment
import kotlinx.coroutines.launch

class ConnectionFragment() :
    ToolbarFragment(R.layout.layout_dashboard_connection),
    Toolbar.OnMenuItemClickListener {

    constructor(conn: Connection) : this() {
        initialConn = conn
    }

    private var initialConn: Connection? = null
    private var uuid: String? = null
        get() = field ?: initialConn?.uuid
        set(value) {
            field = value
        }

    private lateinit var binding: LayoutDashboardConnectionBinding
    private val viewModel by viewModels<ConnectionFragmentViewModel>()
    private val dashboardViewModel by viewModels<DashboardFragmentViewModel>({ requireParentFragment() })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutDashboardConnectionBinding.bind(view)
        toolbar.apply {
            uuid?.let {
                title = it
            }
            inflateMenu(R.menu.connection_menu)
            setOnMenuItemClickListener(this@ConnectionFragment)

            setNavigationIcon(R.drawable.baseline_arrow_back_24)
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
                (requireActivity() as MainActivity).onBackPressedCallback.isEnabled = true
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

        val activity = (requireActivity() as MainActivity)
        activity.onBackPressedCallback.isEnabled = false
        viewModel.initialize(initialConn) {
            activity.connection.service?.queryConnections(dashboardViewModel.connectionState.value.queryOptions)?.connections
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::handleUiState)
            }
        }
    }

    override fun onDestroy() {
        toolbar.setOnLongClickListener(null)
        viewModel.stop()
        super.onDestroy()
    }

    private fun handleUiState(state: ConnectionFragmentState) {
        val conn = state.connection
        uuid = conn.uuid
        toolbar.title = conn.uuid
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

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_close_connection -> {
            (requireActivity() as? MainActivity)?.apply {
                uuid?.let {
                    connection.service?.closeConnection(it)
                }
                supportFragmentManager.popBackStack()
                onBackPressedCallback.isEnabled = true
            }
            true
        }

        else -> false
    }
}