package io.nekohasekai.sagernet.ui

import android.content.ClipData
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet.Companion.clipboardManager
import io.nekohasekai.sagernet.aidl.Connection
import io.nekohasekai.sagernet.databinding.LayoutTrafficBinding
import io.nekohasekai.sagernet.databinding.ViewConnectionItemBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import libcore.Libcore
import moe.matsuri.nb4a.utils.JavaUtil.gson

class TrafficFragment : ToolbarFragment(R.layout.layout_traffic) {

    private lateinit var binding: LayoutTrafficBinding
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

    fun emitStats(list: List<Connection>) {
        if (BuildConfig.DEBUG) Logs.d(list.toString())

        if (list.isEmpty()) {
            runOnMainDispatcher {
                binding.connectionNotFound.isVisible = true
                binding.connections.isVisible = false
            }
            return
        }

        runOnMainDispatcher {
            binding.connectionNotFound.isVisible = false
            binding.connections.isVisible = true
        }

        binding.connections.post {
            adapter.data = list
        }
    }

    inner class TrafficAdapter : RecyclerView.Adapter<Holder>() {
        init {
            setHasStableIds(true)
        }

        lateinit var data: List<Connection>
        private var idStore = linkedSetOf<String>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(ViewConnectionItemBinding.inflate(layoutInflater, parent, false))
        }

        override fun getItemCount(): Int {
            if (!::data.isInitialized) return 0
            return data.size
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            if (idStore.contains(data[position].uuid)) {
                idStore.add(data[position].uuid)
            }
            holder.bind(data[position])
        }

        override fun getItemId(position: Int): Long {
            return idStore.indexOf(data[position].uuid).toLong()
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
            binding.root.setOnClickListener {
                context?.let { ctx ->
                    MaterialAlertDialogBuilder(ctx)
                        .setTitle(R.string.detail)
                        .setMessage(gson.toJson(connection))
                        .setPositiveButton(R.string.ok) { _, _ -> }
                        .setPositiveButton(R.string.action_copy) { _, _ ->
                            clipboardManager.setPrimaryClip(
                                ClipData.newPlainText(
                                    "connection",
                                    gson.toJson(connection),
                                )
                            )
                        }
                        .show()
                }
            }
        }
    }

}