package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutTrafficBinding
import io.nekohasekai.sagernet.databinding.ViewConnectionItemBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.snackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import libcore.HTTPRequest
import libcore.Libcore
import moe.matsuri.nb4a.utils.JavaUtil

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

        try {
            fresh()
        } catch (e: Exception) {
            Logs.e(e.readableMessage)
            snackbar(R.string.not_connected).show()
        }

        lifecycleScope.launch {
            while (isActive) {
                try {
                    fresh()
                } catch (e: Exception) {
                    Logs.e(e.readableMessage)
                }
                delay(500)
            }
        }

        binding.connections.layoutManager = LinearLayoutManager(context)
    }

    private fun fresh() {
        updateList(clash.getConnections())
    }

    private fun updateList(newList: List<Connection>) {
//        Logs.d(newList.toString())
        val diffCallback = ConnectionDiffCallback(connections, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        diffResult.dispatchUpdatesTo(adapter)

        connections.clear()
        connections.addAll(newList)
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
            Logs.d("bind ${connection.id}")
            binding.connectionID.text = "${connection.id} (${connection.metadata.network})"
            binding.connectionTraffic.text = getString(
                R.string.traffic,
                Libcore.formatBytes(connection.upload),
                Libcore.formatBytes(connection.download),
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

    private val clash = ClashManager()

    inner class ClashManager(
        private val resq: HTTPRequest = Libcore.newHttpClient().newRequest().apply {
            setURL("http://${DataStore.clashAPIListen}/connections")
            setUserAgent(USER_AGENT)
            setHeader("Content-Type", "application/json")
        },
    ) {
        fun getConnections(): MutableList<Connection> {
            return JavaUtil.gson.fromJson(
                resq.execute().contentString,
                TrackerInfo::class.java,
            ).connections
        }
    }

    data class Metadata(
        public var network: String = "",
        public var type: String = "",
        public var sourceIP: String = "",
        public var destinationIP: String = "",
        public var sourcePort: String = "",
        public var destinationPort: String = "",
        public var host: String = "",
        public var dnsMode: String = "",
        public var processPath: String = "",
    )

    data class Connection(
        public var id: String = "",
        public var metadata: Metadata = Metadata(),
        public var upload: Long = 0,
        public var download: Long = 0,
        public var start: String = "",
        public var chains: List<String> = listOf(),
        public var rule: String = "",
        public var rulePayload: String = "",
    )

    data class TrackerInfo(
        public var downloadTotal: Long = 0,
        public var uploadTotal: Long = 0,
        public var connections: MutableList<Connection> = mutableListOf(),
        public var memory: Long = 0,
    )

    inner class ConnectionDiffCallback(
        private val oldList: List<Connection>,
        private val newList: List<Connection>,
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

}