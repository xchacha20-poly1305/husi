package io.nekohasekai.sagernet.ui

import android.content.ClipData
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat.getColor
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet.Companion.clipboardManager
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutTrafficBinding
import io.nekohasekai.sagernet.databinding.ViewConnectionItemBinding
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import libcore.Libcore
import moe.matsuri.nb4a.utils.JavaUtil.gson
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
            TrafficSortMode.ID -> toolbar.menu.findItem(R.id.action_sort_id)!!.isChecked = true
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
            TrafficSortMode.ID -> compareValues(a.uuid, b.uuid)
            TrafficSortMode.SRC -> compareValues(a.src, b.src)
            TrafficSortMode.DST -> compareValues(a.dst, b.dst)
            TrafficSortMode.UPLOAD -> compareValues(a.uploadTotal, b.uploadTotal)
            TrafficSortMode.DOWNLOAD -> compareValues(a.downloadTotal, b.downloadTotal)
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

            R.id.action_sort_id -> {
                DataStore.trafficSortMode = TrafficSortMode.ID
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
                it.uuid.contains(str) ||
                        it.network.contains(str) ||
                        it.start.contains(str) ||
                        it.src.contains(str) ||
                        it.dst.contains(str) ||
                        it.host.contains(str) ||
                        it.rule.contains(str)
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
            binding.connectionID.text = textWithColor(
                "${connection.uuid} ",
                getColor(R.color.log_red),
                connection.network,
            )
            binding.connectionSrc.text = textWithColor(
                getString(R.string.source_address) + ": ",
                getColor(R.color.design_default_color_secondary_variant),
                connection.src,
            )
            binding.connectionDst.text = textWithColor(
                getString(R.string.destination_address) + ": ",
                getColor(R.color.color_pink_ssr),
                connection.dst,
            )
            binding.connectionHost.apply {
                if (connection.host.isNotBlank()) {
                    isVisible = true
                    text = textWithColor(
                        "Host: ",
                        getColor(R.color.log_purple),
                        connection.host,
                    )
                } else isVisible = false
            }
            binding.connectionTraffic.text = getString(
                R.string.traffic,
                Libcore.formatBytes(connection.uploadTotal),
                Libcore.formatBytes(connection.downloadTotal),
            )
            binding.connectionStart.text = textWithColor(
                getString(R.string.start) + ": ",
                getColor(R.color.design_default_color_secondary),
                connection.start
            )
            binding.connectionOutbound.text = textWithColor(
                getString(R.string.outbound_rule) + ": ",
                getColor(R.color.log_blue),
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

        private fun textWithColor(
            tagStr: String,
            color: Int,
            colorStr: String,
        ): SpannableStringBuilder {
            return SpannableStringBuilder(tagStr).apply {
                append(colorStr)
                setSpan(
                    ForegroundColorSpan(color),
                    tagStr.length,
                    tagStr.length + colorStr.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        private fun getColor(id: Int): Int {
            return getColor(binding.root.context, id)
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