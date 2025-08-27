package io.nekohasekai.sagernet.ui.dashboard

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.common.hash.Hashing
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.databinding.LayoutDashboardListBinding
import io.nekohasekai.sagernet.databinding.ViewConnectionItemBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColour
import io.nekohasekai.sagernet.ui.MainActivity
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class ConnectionListFragment : Fragment(R.layout.layout_dashboard_list) {

    private lateinit var binding: LayoutDashboardListBinding
    private val viewModel by viewModels<ConnectionListFragmentViewModel>()
    private val dashboardViewModel by viewModels<DashboardFragmentViewModel>({ requireParentFragment() })
    private lateinit var adapter: ConnectionAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutDashboardListBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(binding.recycleView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(8),
                right = bars.right + dp2px(8),
                bottom = bars.bottom + dp2px(64),
            )
            insets
        }
        binding.recycleView.adapter = ConnectionAdapter().also {
            adapter = it
        }
        ItemTouchHelper(SwipeToDeleteCallback(adapter)).attachToRecyclerView(binding.recycleView)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashboardViewModel.uiState.collect {
                    if (it.isPausing) {
                        viewModel.stop()
                    } else {
                        viewModel.initialize((requireActivity() as MainActivity).connection.service!!)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashboardViewModel.connectionState.collect { connectionState ->
                    viewModel.setDescending(connectionState.isDescending)
                    viewModel.updateSortMode(connectionState.sortMode)
                    viewModel.setQueryOptions(connectionState.queryOptions)
                }
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashboardViewModel.searchQuery.collect { query ->
                    viewModel.query = query
                }
            }
        }
    }

    private suspend fun handleUiState(state: ConnectionListFragmentUiState) {
        if (state.connections.isEmpty()) {
            binding.connectionNotFound.isVisible = true
            binding.recycleView.isVisible = false
            adapter.submitList(emptyList())
            return
        }

        binding.connectionNotFound.isVisible = false
        binding.recycleView.isVisible = true
        adapter.submitList(state.connections)
    }

    override fun onPause() {
        viewModel.stop()
        super.onPause()
    }

    private inner class ConnectionAdapter :
        ListAdapter<Connection, Holder>(ConnectionDiffCallback) {

        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                ViewConnectionItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int, payloads: List<Any?>) {
            var mask = 0
            for (payload in payloads) {
                mask = mask or payload as Int
            }
            if (mask == 0) {
                super.onBindViewHolder(holder, position, payloads)
            } else {
                val item = getItem(position)
                if (mask and ConnectionDiffCallback.PAYLOAD_TRAFFIC != 0) {
                    holder.bindTraffic(item.uploadTotal, item.downloadTotal)
                }
                if (mask and ConnectionDiffCallback.PAYLOAD_STATUS != 0) {
                    holder.bindStatus(item.closed)
                }
            }
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position))
        }

        override fun getItemId(position: Int): Long {
            return getItem(position).uuid.murmurHash()
        }

        private fun String.murmurHash(): Long {
            return Hashing.murmur3_128().hashString(this, StandardCharsets.UTF_8).asLong()
        }

    }

    private object ConnectionDiffCallback : DiffUtil.ItemCallback<Connection>() {
        const val PAYLOAD_TRAFFIC = 1 shl 0
        const val PAYLOAD_STATUS = 1 shl 1

        override fun areItemsTheSame(old: Connection, new: Connection): Boolean {
            return old.uuid == new.uuid
        }

        override fun areContentsTheSame(old: Connection, new: Connection): Boolean {
            return old.uploadTotal == new.uploadTotal
                    && old.downloadTotal == new.downloadTotal
                    && old.closed == new.closed
        }

        override fun getChangePayload(old: Connection, new: Connection): Any? {
            var mask = 0
            if (old.uploadTotal != new.uploadTotal || old.downloadTotal != new.downloadTotal) {
                mask = mask or PAYLOAD_TRAFFIC
            }
            if (old.closed != new.closed) {
                mask = mask or PAYLOAD_STATUS
            }
            return if (mask != 0) {
                mask
            } else {
                super.getChangePayload(old, new)
            }
        }
    }

    private inner class Holder(
        private val binding: ViewConnectionItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(connection: Connection) {
            bindStatus(connection.closed)
            var networkText = connection.network.uppercase()
            connection.protocol?.let { networkText += "/" + it.uppercase() }
            binding.connectionNetwork.text = networkText
            binding.connectionInbound.text = connection.inbound
            binding.connectionDestination.text = connection.dst
            binding.connectionHost.let {
                it.isVisible = if (
                    connection.host.isNotBlank() &&
                    // If use domain to connect, not show host.
                    !connection.dst.startsWith(connection.host)
                ) {
                    it.text = connection.host
                    true
                } else {
                    false
                }
            }
            bindTraffic(connection.uploadTotal, connection.downloadTotal)
            binding.connectionChain.text = connection.chain
            binding.root.setOnClickListener {
                (requireActivity() as MainActivity)
                    .supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_holder, ConnectionFragment(connection))
                    .addToBackStack(null)
                    .commit()
            }
        }

        fun bindStatus(closed: Boolean) {
            if (closed) {
                binding.connectionStatus.setText(R.string.connection_status_closed)
                binding.connectionStatus.setTextColor(binding.connectionStatus.context.getColour(R.color.material_red_900))
            } else {
                binding.connectionStatus.setText(R.string.connection_status_active)
                binding.connectionStatus.setTextColor(binding.connectionStatus.context.getColour(R.color.material_green_500))
            }
        }

        fun bindTraffic(upload: Long, download: Long) {
            binding.connectionTraffic.text = binding.connectionTraffic.context.getString(
                R.string.traffic,
                Formatter.formatFileSize(
                    binding.connectionTraffic.context,
                    upload,
                ),
                Formatter.formatFileSize(
                    binding.connectionTraffic.context,
                    download,
                ),
            )
        }
    }

    private inner class SwipeToDeleteCallback(private val adapter: ConnectionAdapter) :
        ItemTouchHelper.Callback() {
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
        ): Int {
            val swipeFlags = ItemTouchHelper.LEFT
            return makeMovementFlags(0, swipeFlags)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            // No move action
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            lifecycleScope.launch {
                (requireActivity() as MainActivity)
                    .connection
                    .service
                    ?.closeConnection(adapter.currentList[viewHolder.absoluteAdapterPosition].uuid)
            }
        }
    }

    fun connectionSize(): Int {
        return adapter.currentList.size
    }
}
