package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutTrafficBinding
import io.nekohasekai.sagernet.databinding.ViewConnectionItemBinding
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import libcore.Libcore
import moe.matsuri.nb4a.utils.setOnFocusCancel

class TrafficFragment : ToolbarFragment(R.layout.layout_traffic),
    Toolbar.OnMenuItemClickListener,
    SearchView.OnQueryTextListener {

    private lateinit var binding: LayoutTrafficBinding
    private lateinit var adapter: TrafficAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutTrafficBinding.bind(view)
        toolbar.setTitle(R.string.menu_dashboard)
        toolbar.inflateMenu(R.menu.traffic_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding.connectionNotFound.isVisible = true

        if (DataStore.trafficDescending) {
            toolbar.menu.findItem(R.id.action_sort_descending)!!.isChecked = true
        } else {
            toolbar.menu.findItem(R.id.action_sort_ascending)!!.isChecked = true
        }
        when (DataStore.trafficSortMode) {
            TrafficSortMode.START -> toolbar.menu.findItem(R.id.action_sort_time)!!.isChecked = true
            TrafficSortMode.INBOUND -> {
                toolbar.menu.findItem(R.id.action_sort_inbound)!!.isChecked = true
            }

            TrafficSortMode.SRC -> toolbar.menu.findItem(R.id.action_sort_source)!!.isChecked = true
            TrafficSortMode.DST -> {
                toolbar.menu.findItem(R.id.action_sort_destination)!!.isChecked = true
            }

            TrafficSortMode.UPLOAD -> {
                toolbar.menu.findItem(R.id.action_sort_upload)!!.isChecked = true
            }

            TrafficSortMode.DOWNLOAD -> {
                toolbar.menu.findItem(R.id.action_sort_download)!!.isChecked = true
            }

            TrafficSortMode.MATCHED_RULE -> {
                toolbar.menu.findItem(R.id.action_sort_rule)!!.isChecked = true
            }
        }
        searchView = toolbar.findViewById<SearchView?>(R.id.action_traffic_search).apply {
            setOnQueryTextListener(this@TrafficFragment)
            maxWidth = Int.MAX_VALUE
            setOnFocusCancel()
        }

        binding.connections.layoutManager = FixedLinearLayoutManager(binding.connections)
        binding.connections.adapter = TrafficAdapter().also {
            adapter = it
        }

        ItemTouchHelper(SwipeToDeleteCallback(adapter)).attachToRecyclerView(binding.connections)

        (requireActivity() as MainActivity).connection.service?.setConnection(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        (requireActivity() as MainActivity).connection.service?.setConnection(false)
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

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_sort_ascending -> {
                DataStore.trafficDescending = false
                item.isChecked = true
            }

            R.id.action_sort_descending -> {
                DataStore.trafficDescending = true
                item.isChecked = true
            }

            R.id.action_sort_time -> {
                DataStore.trafficSortMode = TrafficSortMode.START
                item.isChecked = true
            }

            R.id.action_sort_inbound -> {
                DataStore.trafficSortMode = TrafficSortMode.INBOUND
                item.isChecked = true
            }

            R.id.action_sort_source -> {
                DataStore.trafficSortMode = TrafficSortMode.SRC
                item.isChecked = true
            }

            R.id.action_sort_destination -> {
                DataStore.trafficSortMode = TrafficSortMode.DST
                item.isChecked = true
            }

            R.id.action_sort_upload -> {
                DataStore.trafficSortMode = TrafficSortMode.UPLOAD
                item.isChecked = true
            }

            R.id.action_sort_download -> {
                DataStore.trafficSortMode = TrafficSortMode.DOWNLOAD
                item.isChecked = true
            }

            R.id.action_sort_rule -> {
                DataStore.trafficSortMode = TrafficSortMode.MATCHED_RULE
                item.isChecked = true
            }

            else -> return false
        }
        return true
    }

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

    inner class TrafficAdapter : RecyclerView.Adapter<Holder>() {
        init {
            setHasStableIds(true)
        }

        lateinit var data: MutableList<Connection>

        // Upstream uses UUID (String) as ID, when Adapter use Long.
        // LinkedHashSet marks an unique index for each uuid.
        private var idStore = linkedSetOf<String>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(ViewConnectionItemBinding.inflate(layoutInflater, parent, false))
        }

        override fun getItemCount(): Int {
            if (!::data.isInitialized) return 0
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
            binding.connectionNetwork.text = connection.network.uppercase()
            binding.connectionInbound.text = connection.inbound
            binding.connectionDestination.text = connection.dst
            binding.connectionTraffic.text = getString(
                R.string.traffic,
                Libcore.formatBytes(connection.uploadTotal),
                Libcore.formatBytes(connection.downloadTotal),
            )
            binding.connectionChain.text = connection.chain
            binding.root.setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_holder, ConnectionFragment(connection))
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    inner class SwipeToDeleteCallback(private val adapter: TrafficAdapter) :
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


    private lateinit var searchView: SearchView
    private var searchString: String? = null

    override fun onQueryTextSubmit(query: String?): Boolean {
        searchString = query
        return false
    }

    override fun onQueryTextChange(query: String?): Boolean {
        searchString = query
        return false
    }

}