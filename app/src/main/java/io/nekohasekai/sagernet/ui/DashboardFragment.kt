package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutDashboardBinding
import io.nekohasekai.sagernet.databinding.ViewClashModeBinding
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.getColorAttr
import libcore.Libcore

class DashboardFragment : Fragment(R.layout.layout_dashboard) {

    private lateinit var binding: LayoutDashboardBinding
    private lateinit var adapter: ClashModeAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutDashboardBinding.bind(view)
        binding.clashModeList.layoutManager = FixedLinearLayoutManager(binding.clashModeList)
        binding.clashModeList.adapter = ClashModeAdapter(
            (requireActivity() as MainActivity).connection.service?.clashModes ?: emptyList(),
            io.nekohasekai.sagernet.fmt.CLASH_RULE,
        ).also { adapter = it }
    }

    fun emitStats(memory: Long, goroutines: Int) {
        binding.memoryText.text = Libcore.formatBytes(memory)
        binding.goroutinesText.text = goroutines.toString()
    }

    private inner class ClashModeAdapter(
        val items: List<String>,
        var selected: String,
    ) : RecyclerView.Adapter<ClashModeItemView>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClashModeItemView {
            val view = ClashModeItemView(
                ViewClashModeBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
            view.binding.clashModeButton.clipToOutline = true
            return view
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onBindViewHolder(holder: ClashModeItemView, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class ClashModeItemView(val binding: ViewClashModeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: String) {
            binding.clashModeButtonText.text = item
            if (item != adapter.selected) {
                binding.clashModeButtonText.setTextColor(
                    binding.root.context.getColorAttr(R.attr.colorOnSurface)
                )
                binding.clashModeButton.setBackgroundResource(R.drawable.bg_rounded_rectangle)
                binding.clashModeButton.setOnClickListener {
                    (requireActivity() as MainActivity).connection.service?.setClashMode(item)
                    adapter.selected = item
                    adapter.notifyDataSetChanged()
                }
            } else {
                binding.clashModeButtonText.setTextColor(
                    binding.root.context.getColorAttr(R.attr.colorMaterial100)
                )
                binding.clashModeButton.setBackgroundResource(R.drawable.bg_rounded_rectangle_active)
                binding.clashModeButton.isClickable = false
            }
        }
    }
}