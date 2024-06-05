package io.nekohasekai.sagernet.ui

import android.content.ClipData
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet.Companion.clipboardManager
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.databinding.LayoutTrafficBinding
import io.nekohasekai.sagernet.databinding.ViewConnectionItemBinding
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import libcore.Libcore
import moe.matsuri.nb4a.utils.JavaUtil.gson

class TrafficFragment : ToolbarFragment(R.layout.layout_traffic),
    Toolbar.OnMenuItemClickListener {

    private lateinit var binding: LayoutTrafficBinding
    private lateinit var adapter: TrafficAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutTrafficBinding.bind(view)
        toolbar.setTitle(R.string.menu_dashboard)
        toolbar.inflateMenu(R.menu.traffic_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding.connectionNotFound.isVisible = true

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

    private var descending = false
    private var sortMode = SortMode.START

    enum class SortMode {
        START, ID, SRC, DST, UPLOAD, DOWNLOAD
    }

    private val connectionComparator = Comparator<Connection> { a, b ->
        val result = when (sortMode) {
            SortMode.START -> compareValues(a.start, b.start)
            SortMode.ID -> compareValues(a.uuid, b.uuid)
            SortMode.SRC -> compareValues(a.src, b.src)
            SortMode.DST -> compareValues(a.dst, b.dst)
            SortMode.UPLOAD -> compareValues(a.uploadTotal, b.uploadTotal)
            SortMode.DOWNLOAD -> compareValues(a.downloadTotal, b.downloadTotal)
        }
        if (descending) -result else result
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_sort_ascending -> {
                descending = false
                item.isChecked = true
            }

            R.id.action_sort_descending -> {
                descending = true
                item.isChecked = true
            }

            R.id.action_sort_time -> {
                sortMode = SortMode.START
                item.isChecked = true
            }

            R.id.action_sort_id -> {
                sortMode = SortMode.ID
                item.isChecked = true
            }

            R.id.action_sort_source -> {
                sortMode = SortMode.SRC
                item.isChecked = true
            }

            R.id.action_sort_destination -> {
                sortMode = SortMode.DST
                item.isChecked = true
            }

            R.id.action_sort_upload -> {
                sortMode = SortMode.UPLOAD
                item.isChecked = true
            }

            R.id.action_sort_download -> {
                sortMode = SortMode.DOWNLOAD
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

        val newList = list.sortedWith(connectionComparator).toMutableList()

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

        // Upstream uses UUID as ID, when Adapter use Long.
        // LinkedHashSet mark an unique index for each uuid string.
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
            binding.connectionID.text = "${connection.uuid} (${connection.network})"
            binding.connectionSrc.text = getString(R.string.source_address, connection.src)
            binding.connectionDst.text = getString(R.string.destination_address, connection.dst)
            binding.connectionTraffic.text = getString(
                R.string.traffic,
                Libcore.formatBytes(connection.uploadTotal),
                Libcore.formatBytes(connection.downloadTotal),
            )
            binding.connectionStart.text = getString(
                R.string.start_time,
                connection.start,
            )
            binding.connectionOutbound.text = getString(
                R.string.outbound_rule,
                connection.rule,
            )
            binding.root.setOnClickListener {
                context?.let { ctx ->
                    val detail = gson.toJson(connection)
                    MaterialAlertDialogBuilder(ctx)
                        .setTitle(R.string.detail)
                        .setMessage(detail)
                        .setPositiveButton(R.string.ok) { _, _ -> }
                        .setPositiveButton(R.string.action_copy) { _, _ ->
                            clipboardManager.setPrimaryClip(
                                ClipData.newPlainText(
                                    "connection",
                                    detail,
                                )
                            )
                            snackbar(R.string.copy_success)
                        }
                        .show()
                }
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


}