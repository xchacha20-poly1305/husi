package io.nekohasekai.sagernet.ui.dashboard

import android.annotation.SuppressLint
import android.view.View
import androidx.core.view.ViewCompat
import android.view.LayoutInflater
import androidx.core.view.isInvisible
import android.text.TextWatcher
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ProxySet
import io.nekohasekai.sagernet.aidl.ProxySetItem
import io.nekohasekai.sagernet.databinding.LayoutProxySetBinding
import io.nekohasekai.sagernet.databinding.LayoutStatusListBinding
import io.nekohasekai.sagernet.databinding.ViewProxySetItemBinding
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sfa.utils.ColorUtils.colorForURLTestDelay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class ProxySetFragment : Fragment(R.layout.layout_status_list) {

    private lateinit var binding: LayoutStatusListBinding
    private lateinit var adapter: Adapter
    private var job: Job? = null

    private val context get() = requireContext() as MainActivity

    override fun onViewCreated(view: View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutStatusListBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(binding.recycleView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(8),
                right = bars.right + dp2px(8),
                bottom = bars.bottom + dp2px(8),
            )
            WindowInsetsCompat.CONSUMED
        }

        binding.recycleView.layoutManager = FixedLinearLayoutManager(binding.recycleView)
        binding.recycleView.adapter = Adapter().also {
            adapter = it
        }

        job = runOnDefaultDispatcher {
            val context = context
            while(isActive) {
                val sets = context.connection.service?.queryProxySet() ?: continue
                onMainDispatcher {
                    adapter.freshProxySets(sets)
                }
                delay(2000)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        job?.cancel()
    }

    inner class Adapter : RecyclerView.Adapter<ProxySetView>() {

        private var proxySets= mutableListOf<ProxySet>()

        @SuppressLint("NotifyDataSetChanged")
        suspend fun freshProxySets(newSets: List<ProxySet>) {
            binding.connectionNotFound.isVisible = newSets.isEmpty()
            if (proxySets.size != newSets.size) {
                proxySets = newSets.toMutableList()
                notifyDataSetChanged()
            } else {
                newSets.forEachIndexed { index, proxySet ->
                    if (this.proxySets[index] != proxySet) {
                        this.proxySets[index] = proxySet
                        notifyItemChanged(index)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProxySetView {
            return ProxySetView(
                LayoutProxySetBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                ),
            )
        }

        override fun getItemCount(): Int {
            return proxySets.size
        }

        override fun onBindViewHolder(holder: ProxySetView, position: Int) {
            holder.bind(proxySets[position])
        }
    }

    inner class ProxySetView(val binding: LayoutProxySetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private lateinit var proxySet: ProxySet
        private lateinit var items: List<ProxySetItem>
        private lateinit var adapter: ItemAdapter
        private var textWatcher: TextWatcher? = null

        @SuppressLint("NotifyDataSetChanged")
        fun bind(proxySet: ProxySet) {
            this.proxySet = proxySet
            binding.groupName.text = proxySet.tag
            binding.groupType.text = proxySet.type
            binding.urlTestButton.setOnClickListener {
                runOnDefaultDispatcher {
                    // TODO
                    context.connection.service?.urlTest()
                }
            }
            items = proxySet.items
            if (!::adapter.isInitialized) {
                adapter = ItemAdapter(this, proxySet, items.toMutableList())
                binding.itemList.adapter = adapter
                (binding.itemList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations =
                    false
                binding.itemList.layoutManager = GridLayoutManager(binding.root.context, 2)
            } else {
                adapter.proxySet = proxySet
                adapter.setItems(items)
            }
            updateExpand(false)
        }

        private fun updateExpand(isExpand: Boolean) {
            binding.itemList.isVisible = isExpand
            binding.groupSelected.isVisible = !isExpand
            val textView = (binding.groupSelected.editText as MaterialAutoCompleteTextView)
            if (textWatcher != null) {
                textView.removeTextChangedListener(textWatcher)
            }
            if (!isExpand) {
                binding.groupSelected.hint = proxySet.selected
                binding.groupSelected.isEnabled = proxySet.selectable
                if (proxySet.selectable) {
                    textView.setSimpleItems(proxySet.items.toList().map { it.tag }.toTypedArray())
                    textWatcher = textView.addTextChangedListener {
                        val selected = textView.text.toString()
                        if (selected != proxySet.selected) {
                            updateSelected(proxySet, selected)
                        }
                        runOnDefaultDispatcher {
                            context.connection.service?.groupSelect(proxySet.tag, selected)
                        }
                    }
                }
            }
            if (isExpand) {
                binding.urlTestButton.isVisible = true
                binding.expandButton.setImageResource(R.drawable.ic_expand_less_24)
            } else {
                binding.urlTestButton.isVisible = false
                binding.expandButton.setImageResource(R.drawable.ic_expand_more_24)
            }
            binding.expandButton.setOnClickListener {
                updateExpand(!binding.itemList.isVisible)
            }
        }

        fun updateSelected(proxySet: ProxySet, itemTag: String) {
            val oldSelected = items.indexOfFirst { it.tag == proxySet.selected }
            proxySet.selected = itemTag
            if (oldSelected != -1) {
                adapter.notifyItemChanged(oldSelected)
            }
        }
    }

    inner class ItemAdapter(
        val proxySetView: ProxySetView,
        var proxySet: ProxySet,
        private var items: MutableList<ProxySetItem> = mutableListOf()
    ) : RecyclerView.Adapter<ItemProxySetView>() {

        @SuppressLint("NotifyDataSetChanged")
        fun setItems(newItems: List<ProxySetItem>) {
            if (items.size != newItems.size) {
                items = newItems.toMutableList()
                notifyDataSetChanged()
            } else {
                newItems.forEachIndexed { index, item ->
                    if (items[index] != item) {
                        items[index] = item
                        notifyItemChanged(index)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemProxySetView {
            return ItemProxySetView(
                ViewProxySetItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onBindViewHolder(holder: ItemProxySetView, position: Int) {
            holder.bind(proxySetView, proxySet, items[position])
        }
    }

    inner class ItemProxySetView(val binding: ViewProxySetItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(proxySetView: ProxySetView, proxySet: ProxySet, item: ProxySetItem) {
            if (proxySet.selectable) {
                binding.itemCard.setOnClickListener {
                    binding.selectedView.isVisible = true
                    proxySetView.updateSelected(proxySet, item.tag)
                    runOnDefaultDispatcher {
                        context.connection.service?.groupSelect(proxySet.tag, item.tag)
                    }
                }
            }
            binding.selectedView.isInvisible = proxySet.selected != item.tag
            binding.itemName.text = item.tag
            binding.itemType.text = item.type
            binding.itemStatus.isVisible = item.urlTestDelay > 0
            if (item.urlTestDelay >= 0) {
                binding.itemStatus.text = "${item.urlTestDelay}ms"
                binding.itemStatus.setTextColor(
                    colorForURLTestDelay(
                        binding.root.context,
                        item.urlTestDelay,
                    )
                )
            }
        }
    }
}