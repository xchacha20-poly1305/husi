package io.nekohasekai.sagernet.ui.dashboard

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutConnectionBinding
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ToolbarFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import libcore.Libcore

class ConnectionFragment(val conn: Connection) :
    ToolbarFragment(R.layout.layout_connection),
    Toolbar.OnMenuItemClickListener {

    private lateinit var binding: LayoutConnectionBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutConnectionBinding.bind(view)
        toolbar.apply {
            setTitle(conn.uuid)
            inflateMenu(R.menu.connection_menu)
            setOnMenuItemClickListener(this@ConnectionFragment)

            setNavigationIcon(R.drawable.baseline_arrow_back_24)
            setNavigationOnClickListener {
                parentFragmentManager.popBackStack()
                (requireActivity() as MainActivity).onBackPressedCallback.isEnabled = true
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = statusBarTop)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
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

        val activity = (requireActivity() as MainActivity)
        activity.onBackPressedCallback.isEnabled = false

        bind()

        lifecycleScope.launch {
            val interval = DataStore.speedInterval.takeIf { it > 0 }?.toLong() ?: 1000L
            while(isActive) {
                activity.connection.service?.queryConnections()?.let {
                    emitStats(it.connections)
                }
                delay(interval)
            }
        }
    }

    private fun bind() {
        binding.connNetwork.text = conn.network.uppercase()
        if (conn.protocol != null) {
            binding.connProtocol.isVisible = true
            binding.connProtocol.text = conn.protocol.uppercase()
        } else {
            binding.connProtocol.isVisible = false
        }
        binding.connTime.text = conn.start
        if (conn.ipVersion != null) {
            binding.connIPVersionLayout.isVisible = true
            binding.connIPVersion.text = conn.ipVersion.toString()
        } else {
            binding.connIPVersionLayout.isVisible = false
        }
        bindTraffic()
        binding.connInbound.text = conn.inbound
        binding.connSource.text = conn.src
        binding.connDestination.text = conn.dst
        if (conn.host.isNotBlank()) {
            binding.connHostLayout.isVisible = true
            binding.connHost.text = conn.host
        } else {
            binding.connHostLayout.isVisible = false
        }
        binding.connRule.text = conn.matchedRule
        binding.connOutbound.text = conn.outbound
        binding.connChain.text = conn.chain
    }

    private fun bindTraffic() {
        binding.connUpload.text = Libcore.formatBytes(conn.uploadTotal)
        binding.connDownload.text = Libcore.formatBytes(conn.downloadTotal)
    }

    private suspend fun emitStats(connections: List<Connection>) {
        val new = connections.find { it.uuid == conn.uuid } ?: return
        onMainDispatcher {
            conn.apply {
                uploadTotal = new.uploadTotal
                downloadTotal = new.downloadTotal
            }
            bindTraffic()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_close_connection -> {
                (requireActivity() as? MainActivity)?.apply {
                    connection.service?.closeConnection(conn.uuid)
                    supportFragmentManager.popBackStack()
                }
                return true
            }
        }
        return false
    }
}