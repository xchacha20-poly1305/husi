package io.nekohasekai.sagernet.ui.dashboard

import android.os.Bundle
import android.text.TextWatcher
import android.view.View
import androidx.core.view.ViewCompat
import android.view.LayoutInflater
import android.view.animation.AlphaAnimation
import androidx.core.view.isInvisible
import android.view.ViewGroup
import android.view.animation.Animation
import androidx.annotation.DrawableRes
import androidx.core.widget.addTextChangedListener
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProxySetFragment : Fragment(R.layout.layout_status_list) {

    private lateinit var binding: LayoutStatusListBinding
    private lateinit var adapter: Adapter

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
        binding.recycleView.adapter = Adapter().also {
            adapter = it
        }

        lifecycleScope.launch {
            while (isActive) {
                context.connection.service?.queryProxySet()?.let {
                    onMainDispatcher {
                        adapter.refreshProxySets(it)
                    }
                }
                delay(2000)
            }
        }
    }

    inner class Adapter : RecyclerView.Adapter<ProxySetView>() {

        private var proxySets = mutableListOf<ProxySet>()

        fun refreshProxySets(newSets: List<ProxySet>) {
            val old = proxySets.toList()
            newSets.forEach { newSet ->
                old.find { it.tag == newSet.tag }?.let { existing ->
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
                    return o.items == n.items && o.delays == n.delays
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

    inner class ProxySetView(val binding: LayoutProxySetBinding) :
        RecyclerView.ViewHolder(binding.root) {

        lateinit var proxySet: ProxySet
        private lateinit var itemAdapter: ItemAdapter
        private var textWatcher: TextWatcher? = null

        fun bind(set: ProxySet) {
            proxySet = set
            binding.proxySetName.text = set.tag
            binding.proxySetType.text = set.type
            binding.proxySetSelected.isVisible = !set.isExpanded
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

                    onMainDispatcher {
                        binding.urlTestButton.let {
                            it.clearAnimation()
                            it.isEnabled = true
                        }

                        if (result != null) {
                            result.data.forEach { (tag, delay) ->
                                set.delays[tag] = delay
                            }
                            itemAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }

            @DrawableRes
            fun expandButton(isExpanded: Boolean): Int = if (isExpanded) {
                R.drawable.ic_expand_less_24
            } else {
                R.drawable.ic_expand_more_24
            }
            binding.expandButton.setImageResource(expandButton(set.isExpanded))
            binding.expandButton.setOnClickListener {
                set.isExpanded = !set.isExpanded
                binding.expandButton.setImageResource(expandButton(set.isExpanded))
                binding.itemList.isVisible = set.isExpanded
                binding.proxySetSelected.isVisible = !set.isExpanded
            }

            val textView = binding.proxySetSelected.editText as MaterialAutoCompleteTextView
            textView.apply {
                threshold = 0
                if (textWatcher != null) {
                    removeTextChangedListener(textWatcher)
                }
            }
            textView.setText(set.selected, false)
            if (!set.isExpanded) {
                binding.proxySetSelected.hint = set.selected
                binding.proxySetSelected.isEnabled = set.selectable
                if (set.selectable) {
                    textView.setSimpleItems(set.items.map { it.tag }.toTypedArray())
                    textWatcher = textView.addTextChangedListener {
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
                itemAdapter = ItemAdapter(this)
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

    inner class ItemAdapter(private val setView: ProxySetView) :
        RecyclerView.Adapter<ItemProxySetView>() {

        private var items = setView.proxySet.items.toMutableList()

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
            holder.bind(setView, items[position], this)
        }
    }

    inner class ItemProxySetView(
        private val binding: ViewProxySetItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(view: ProxySetView, item: ProxySetItem, adapter: ItemAdapter) {
            binding.itemName.text = item.tag
            binding.itemType.text = item.type
            val delay = view.proxySet.delays[item.tag] ?: -1
            binding.itemStatus.isVisible = delay >= 0
            if (delay >= 0) {
                binding.itemStatus.text = "${delay}ms"
                binding.itemStatus.setTextColor(
                    colorForURLTestDelay(binding.root.context, delay)
                )
            }

            binding.selectedView.isInvisible = view.proxySet.selected != item.tag
            if (view.proxySet.selectable) {
                binding.itemCard.setOnClickListener {
                    val old = view.proxySet.selected
                    view.proxySet.selected = item.tag
                    view.binding.proxySetSelected.editText?.hint = item.tag
                    adapter.notifySelectionChanged(old, item.tag)
                    runOnDefaultDispatcher {
                        context.connection.service?.groupSelect(view.proxySet.tag, item.tag)
                    }
                }
            }
        }
    }
}