package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.databinding.LayoutTrafficBinding
import io.nekohasekai.sagernet.databinding.ViewConnectionItemBinding
import io.nekohasekai.sagernet.ktx.Logs
import libcore.Libcore

class TrafficFragment : ToolbarFragment(R.layout.layout_traffic) {

    private lateinit var binding: LayoutTrafficBinding
    private var connections = mutableListOf<Connection>()
    private lateinit var adapter: TrafficAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutTrafficBinding.inflate(layoutInflater)
        toolbar.setTitle(R.string.menu_dashboard)

        binding.connections.adapter = TrafficAdapter().also {
            adapter = it
        }

        binding.connections.layoutManager = LinearLayoutManager(context)
    }

    fun emitStats(conns: List<Connection>) {
        if (BuildConfig.DEBUG) Logs.d(conns.toString())
        val diffCallback = ConnectionDiffCallback(connections, conns)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        diffResult.dispatchUpdatesTo(adapter)

        connections.clear()
        connections.addAll(conns)
    }

    inner class TrafficAdapter : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(ViewConnectionItemBinding.inflate(layoutInflater, parent, false))
        }

        override fun getItemCount(): Int {
            return connections.size
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(connections[position])
        }

    }

    inner class Holder(
        private val binding: ViewConnectionItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(connection: Connection) {
            Logs.d("bind ${connection.uuid}")
            binding.connectionID.text = "${connection.uuid} (${connection.network})"
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
        }
    }

    inner class ConnectionDiffCallback(
        private val oldList: List<Connection>,
        private val newList: List<Connection>,
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].uuid == newList[newItemPosition].uuid
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

}