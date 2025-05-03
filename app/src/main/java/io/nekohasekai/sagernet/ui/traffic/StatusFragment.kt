package io.nekohasekai.sagernet.ui.traffic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.databinding.LayoutStatusBinding
import io.nekohasekai.sagernet.databinding.ViewClashModeBinding
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ui.MainActivity
import libcore.Libcore

class StatusFragment : Fragment(R.layout.layout_status) {

    private lateinit var binding: LayoutStatusBinding
    private lateinit var adapter: ClashModeAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutStatusBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(binding.clashModeList) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(4),
                right = bars.right + dp2px(4),
                bottom = bars.bottom + dp2px(4),
            )
            WindowInsetsCompat.CONSUMED
        }
        binding.clashModeList.layoutManager = FixedLinearLayoutManager(binding.clashModeList)

        val service = (requireActivity() as MainActivity).connection.service
        val clashModes = service?.clashModes ?: emptyList()
        val selected = service?.clashMode ?: RuleEntity.MODE_RULE
        binding.clashModeList.adapter = ClashModeAdapter(clashModes, selected).also {
            adapter = it
        }
    }

    fun emitStats(memory: Long, goroutines: Int) {
        binding.memoryText.text = Libcore.formatMemoryBytes(memory)
        binding.goroutinesText.text = goroutines.toString()
    }

    fun clearStats() {
        binding.memoryText.text = getString(R.string.no_statistics)
        binding.goroutinesText.text = getString(R.string.no_statistics)
    }

    fun refreshClashMode() {
        val service = (requireActivity() as MainActivity).connection.service
        adapter.items = service?.clashModes ?: emptyList()
        adapter.selected = service?.clashMode ?: RuleEntity.MODE_RULE
        adapter.notifyDataSetChanged()
    }

    private inner class ClashModeAdapter(
        var items: List<String>,
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
            val mode = items[position]
            holder.bind(mode, mode == selected) {
                adapter.selected = mode
                adapter.notifyDataSetChanged()
            }
        }
    }

    private inner class ClashModeItemView(val binding: ViewClashModeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: String, isSelected: Boolean, updateSelected: () -> Unit) {
            binding.clashModeButtonText.text = item
            if (isSelected) {
                binding.clashModeButtonText.setTextColor(
                    binding.root.context.getColorAttr(com.google.android.material.R.attr.tabSelectedTextColor)
                )
                binding.clashModeButton.setBackgroundResource(R.drawable.bg_rounded_rectangle_active)
                binding.clashModeButton.isClickable = false
            } else {
                binding.clashModeButtonText.setTextColor(
                    binding.root.context.getColorAttr(R.attr.primaryOrTextPrimary)
                )
                binding.clashModeButton.setBackgroundResource(R.drawable.bg_rounded_rectangle)
                binding.clashModeButton.setOnClickListener {
                    (requireActivity() as MainActivity).connection.service?.setClashMode(item)
                    updateSelected()
                }
            }
        }
    }
}