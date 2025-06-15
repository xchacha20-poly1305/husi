package io.nekohasekai.sagernet.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import android.view.LayoutInflater
import android.view.animation.AlphaAnimation
import androidx.core.view.isInvisible
import android.view.ViewGroup
import android.view.animation.Animation
import androidx.core.widget.addTextChangedListener
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ProxySet
import io.nekohasekai.sagernet.aidl.ProxySetItem
import io.nekohasekai.sagernet.database.DataStore
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutStatusListBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(binding.recycleView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(8),
                right = bars.right + dp2px(8),
                bottom = bars.bottom + dp2px(8)
            )
            WindowInsetsCompat.CONSUMED
        }

        binding.recycleView.layoutManager = FixedLinearLayoutManager(binding.recycleView)
        binding.recycleView.adapter = Adapter().also { adapter = it }

        job = runOnDefaultDispatcher {
            while (isActive) {
                val sets = context.connection.service?.queryProxySet() ?: continue
                onMainDispatcher {
                    adapter.refreshProxySets(sets)
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

        private var proxySets = mutableListOf<ProxySet>()

        fun refreshProxySets(newSets: List<ProxySet>) {
            val old = proxySets.toList()
            newSets.forEach { newSet ->
                old.find { it.tag == newSet.tag }?.let { existing ->
                    newSet.delays.clear()
                    newSet.delays.putAll(existing.delays)
                    newSet.isExpanded = existing.isExpanded
                }
            }
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = newSets.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                    old[oldPos].tag == newSets[newPos].tag

                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val o = old[oldPos]
                    val n = newSets[newPos]
                    return o.selected == n.selected &&
                            o.isExpanded == n.isExpanded &&
                            o.items == n.items &&
                            o.delays == n.delays
                }
            })

            proxySets.clear()
            proxySets.addAll(newSets)
            diff.dispatchUpdatesTo(this)
            binding.connectionNotFound.isVisible = proxySets.isEmpty()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ProxySetView(
                LayoutProxySetBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

        override fun getItemCount() = proxySets.size

        override fun onBindViewHolder(holder: ProxySetView, position: Int) {
            holder.bind(proxySets[position])
        }
    }

    inner class ProxySetView(private val binding: LayoutProxySetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private lateinit var proxySet: ProxySet
        private lateinit var itemAdapter: ItemAdapter

        fun bind(set: ProxySet) {
            proxySet = set
            binding.groupName.text = set.tag
            binding.groupType.text = set.type
            binding.groupSelected.isVisible = !set.isExpanded
            binding.itemList.isVisible = set.isExpanded

            binding.urlTestButton.setOnClickListener {
                binding.urlTestButton.let {
                    it.isEnabled = false

                    val blinkAnimation = AlphaAnimation(1.0f, 0.3f).apply {
                        duration = 500
                        repeatMode = Animation.REVERSE
                        repeatCount = Animation.INFINITE
                    }
                    it.startAnimation(blinkAnimation)
                }

                runOnDefaultDispatcher {
                    val result = context.connection.service
                        ?.groupURLTest(set.tag, DataStore.connectionTestTimeout)
                        ?: return@runOnDefaultDispatcher
                    onMainDispatcher {
                        result.data.forEach { (tag, delay) ->
                            set.delays[tag] = delay
                        }
                        itemAdapter.notifyDataSetChanged()

                        binding.urlTestButton.let {
                            it.clearAnimation()
                            it.isEnabled = true
                        }
                    }
                }
            }

            binding.expandButton.setImageResource(
                if (set.isExpanded) R.drawable.ic_expand_less_24 else R.drawable.ic_expand_more_24
            )
            binding.expandButton.setOnClickListener {
                set.isExpanded = !set.isExpanded
                binding.itemList.isVisible = set.isExpanded
                binding.groupSelected.isVisible = !set.isExpanded
            }

            val textView = binding.groupSelected.editText as MaterialAutoCompleteTextView
            if (!set.isExpanded) {
                binding.groupSelected.hint = set.selected
                binding.groupSelected.isEnabled = set.selectable
                if (set.selectable) {
                    textView.setSimpleItems(set.items.map { it.tag }.toTypedArray())
                    textView.addTextChangedListener {
                        val selectedTag = it.toString()
                        if (selectedTag != set.selected) {
                            val old = set.selected
                            set.selected = selectedTag
                            itemAdapter.notifySelectionChanged(old, selectedTag)
                            runOnDefaultDispatcher {
                                context.connection.service?.groupSelect(set.tag, selectedTag)
                            }
                        }
                    }
                }
            }

            if (!::itemAdapter.isInitialized) {
                itemAdapter = ItemAdapter(set)
                binding.itemList.apply {
                    adapter = itemAdapter
                    layoutManager = GridLayoutManager(context, 2)
                    (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
                }
            } else {
                itemAdapter.updateItems(set)
            }
        }
    }

    inner class ItemAdapter(private val proxySet: ProxySet) :
        RecyclerView.Adapter<ItemProxySetView>() {

        private var items = proxySet.items.toMutableList()

        fun updateItems(set: ProxySet) {
            items = set.items.toMutableList()
            notifyDataSetChanged()
        }

        fun notifySelectionChanged(oldTag: String, newTag: String) {
            val oldIndex = items.indexOfFirst { it.tag == oldTag }
            val newIndex = items.indexOfFirst { it.tag == newTag }
            if (oldIndex >= 0) notifyItemChanged(oldIndex)
            if (newIndex >= 0) notifyItemChanged(newIndex)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ItemProxySetView(
                ViewProxySetItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: ItemProxySetView, position: Int) {
            holder.bind(proxySet, items[position], this)
        }
    }

    inner class ItemProxySetView(
        private val binding: ViewProxySetItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(set: ProxySet, item: ProxySetItem, adapter: ItemAdapter) {
            binding.itemName.text = item.tag
            binding.itemType.text = item.type
            val delay = set.delays[item.tag] ?: -1
            binding.itemStatus.isVisible = delay >= 0
            if (delay >= 0) {
                binding.itemStatus.text = "${delay}ms"
                binding.itemStatus.setTextColor(
                    colorForURLTestDelay(binding.root.context, delay)
                )
            }

            binding.selectedView.isInvisible = set.selected != item.tag
            if (set.selectable) {
                binding.itemCard.setOnClickListener {
                    val old = set.selected
                    set.selected = item.tag
                    adapter.notifySelectionChanged(old, item.tag)
                    runOnDefaultDispatcher {
                        context.connection.service?.groupSelect(set.tag, item.tag)
                    }
                }
            }
        }
    }
}