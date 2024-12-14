package io.nekohasekai.sagernet.ui.traffic

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutConnectionListBinding
import io.nekohasekai.sagernet.databinding.ViewConnectionItemBinding
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ui.MainActivity
import libcore.Libcore

class ConnectionListFragment : Fragment(R.layout.layout_connection_list) {

    lateinit var binding: LayoutConnectionListBinding
    lateinit var adapter: ConnectionAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutConnectionListBinding.bind(view)
        binding.connections.layoutManager = FixedLinearLayoutManager(binding.connections)
        binding.connections.adapter = ConnectionAdapter().also {
            adapter = it
        }
        ItemTouchHelper(SwipeToDeleteCallback(adapter)).attachToRecyclerView(binding.connections)
    }

    inner class ConnectionAdapter : RecyclerView.Adapter<Holder>() {
        init {
            setHasStableIds(true)
        }

        var data: MutableList<Connection> = mutableListOf()

        // Upstream uses UUID (String) as ID, when Adapter use Long.
        // LinkedHashSet marks an unique index for each uuid.
        private var idStore = linkedSetOf<String>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(ViewConnectionItemBinding.inflate(layoutInflater, parent, false))
        }

        override fun getItemCount(): Int {
            return data.size
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            idStore.add(data[position].uuid)
            holder.bind(data[position])
        }

        override fun getItemId(position: Int): Long {
            idStore.add(data[position].uuid)
            return idStore.indexOf(data[position].uuid).toLong()
        }

    }

    inner class Holder(
        private val binding: ViewConnectionItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(connection: Connection) {
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
            binding.connectionTraffic.text = getString(
                R.string.traffic,
                Libcore.formatBytes(connection.uploadTotal),
                Libcore.formatBytes(connection.downloadTotal),
            )
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
    }

    inner class SwipeToDeleteCallback(private val adapter: ConnectionAdapter) :
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
            (requireActivity() as MainActivity)
                .connection
                .service?.closeConnection(adapter.data[viewHolder.absoluteAdapterPosition].uuid)
        }
    }

    private val connectionComparator = Comparator<Connection> { a, b ->
        var result = when (DataStore.trafficSortMode) {
            TrafficSortMode.START -> compareValues(a.start, b.start)
            TrafficSortMode.INBOUND -> compareValues(a.uuid, b.uuid)
            TrafficSortMode.SRC -> compareValues(a.src, b.src)
            TrafficSortMode.DST -> compareValues(a.dst, b.dst)
            TrafficSortMode.UPLOAD -> compareValues(a.uploadTotal, b.uploadTotal)
            TrafficSortMode.DOWNLOAD -> compareValues(a.downloadTotal, b.downloadTotal)
            TrafficSortMode.MATCHED_RULE -> compareValues(a.matchedRule, b.matchedRule)
            else -> throw IllegalArgumentException()
        }

        // If same, sort by uuid
        if (result == 0) result = compareValues(a.uuid, b.uuid)

        if (DataStore.trafficDescending) -result else result
    }

    var searchString: String? = null

    fun emitStats(list: List<Connection>) {
        if (list.isEmpty()) {
            runOnMainDispatcher {
                binding.connectionNotFound.isVisible = true
                binding.connections.isVisible = false
            }
            return
        }

        val newList = list.filter {
            searchString?.let { str ->
                it.inbound.contains(str) ||
                        it.network.contains(str) ||
                        it.start.contains(str) ||
                        it.src.contains(str) ||
                        it.dst.contains(str) ||
                        it.host.contains(str) ||
                        it.matchedRule.contains(str) ||
                        it.outbound.contains(str) ||
                        it.chain.contains(str)
            } ?: true
        }.sortedWith(connectionComparator).toMutableList()

        runOnMainDispatcher {
            binding.connectionNotFound.isVisible = false
            binding.connections.isVisible = true
        }

        binding.connections.post {
            adapter.data = newList
            adapter.notifyDataSetChanged()
        }
    }
}