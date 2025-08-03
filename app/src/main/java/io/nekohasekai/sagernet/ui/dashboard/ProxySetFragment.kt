package io.nekohasekai.sagernet.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.GridLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ProxySetItem
import io.nekohasekai.sagernet.databinding.LayoutDashboardListBinding
import io.nekohasekai.sagernet.databinding.LayoutProxySetBinding
import io.nekohasekai.sagernet.databinding.ViewProxySetItemBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sfa.utils.ColorUtils.colorForURLTestDelay
import kotlinx.coroutines.launch

class ProxySetFragment : Fragment(R.layout.layout_dashboard_list) {

    private lateinit var binding: LayoutDashboardListBinding
    private val viewModel by viewModels<ProxySetFragmentViewModel>()
    private val adapter by lazy {
        Adapter(
            onExpandClick = viewModel::setExpanded,
            onUrlTestClick = viewModel::performUrlTest,
            onSelectItem = viewModel::select,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutDashboardListBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(binding.recycleView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(8),
                right = bars.right + dp2px(8),
                bottom = bars.bottom + dp2px(64)
            )
            insets
        }

        binding.recycleView.adapter = adapter

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? MainActivity)?.connection?.service?.let {
            viewModel.initialize(it)
        }
    }

    private fun handleUiState(state: ProxySetFragmentUiState) {
        if (state.proxySets.isEmpty()) {
            binding.connectionNotFound.isVisible = true
            binding.recycleView.isVisible = false
            adapter.submitList(emptyList())
            return
        }

        binding.connectionNotFound.isVisible = false
        binding.recycleView.isVisible = true
        adapter.submitList(state.proxySets.values.toList())
    }

    override fun onPause() {
        viewModel.stop()
        super.onPause()
    }

    private inner class Adapter(
        private val onExpandClick: (group: String, isExpanded: Boolean) -> Unit,
        private val onUrlTestClick: (group: String) -> Unit,
        private val onSelectItem: (group: String, tag: String) -> Unit,
    ) : ListAdapter<ProxySetData, ProxySetView>(proxySetCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ProxySetView(
                LayoutProxySetBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                ),
                onExpandClick,
                onUrlTestClick,
                onSelectItem,
            )

        override fun onBindViewHolder(holder: ProxySetView, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private val proxySetCallback = object : DiffUtil.ItemCallback<ProxySetData>() {
        override fun areItemsTheSame(old: ProxySetData, new: ProxySetData): Boolean {
            return old.proxySet.tag == new.proxySet.tag && old.proxySet.type == new.proxySet.type
        }

        override fun areContentsTheSame(old: ProxySetData, new: ProxySetData): Boolean {
            // selectable depends on type, so we don't compare it here
            return old.isExpanded == new.isExpanded
                    && old.isTesting == new.isTesting
                    && old.proxySet.selected == new.proxySet.selected
                    && compareItems(old.proxySet.items, new.proxySet.items) // most complex, last
        }

        private fun compareItems(old: List<ProxySetItem>, new: List<ProxySetItem>): Boolean {
            if (old.size != new.size) return false
            for (i in old.indices) {
                if (old[i].tag != new[i].tag || old[i].type != new[i].type || old[i].urlTestDelay != new[i].urlTestDelay) {
                    return false
                }
            }
            return true
        }
    }

    private inner class ProxySetView(
        val binding: LayoutProxySetBinding,
        private val onExpandClick: (group: String, isExpanded: Boolean) -> Unit,
        private val onUrlTestClick: (group: String) -> Unit,
        private val onSelectItem: (group: String, tag: String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isProgrammaticChange = false

        init {
            val textView = binding.proxySetSelected.editText as MaterialAutoCompleteTextView
            textView.addTextChangedListener { text ->
                if (!isProgrammaticChange && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    val selectedTag = text.toString()
                    adapter.currentList.getOrNull(bindingAdapterPosition)?.let {
                        if (selectedTag.isNotEmpty() && selectedTag != it.proxySet.selected) {
                            onSelectItem(it.proxySet.tag, selectedTag)
                        }
                    }
                }
            }
        }

        fun bind(data: ProxySetData) {
            binding.proxySetName.text = data.proxySet.tag
            binding.proxySetType.text = data.proxySet.type
            binding.proxySetSelected.isVisible = !data.isExpanded
            binding.itemList.isVisible = data.isExpanded

            binding.urlTestButton.isEnabled = !data.isTesting
            if (data.isTesting) {
                val blinkAnimation = AlphaAnimation(1.0f, 0.3f).apply {
                    duration = 500
                    repeatMode = Animation.REVERSE
                    repeatCount = Animation.INFINITE
                }
                binding.urlTestButton.startAnimation(blinkAnimation)
            } else {
                binding.urlTestButton.clearAnimation()
            }
            binding.urlTestButton.setOnClickListener {
                onUrlTestClick(data.proxySet.tag)
            }

            binding.expandButton.setImageResource(
                if (data.isExpanded) {
                    R.drawable.ic_expand_less_24
                } else {
                    R.drawable.ic_expand_more_24
                }
            )
            binding.expandButton.setOnClickListener {
                onExpandClick(data.proxySet.tag, !data.isExpanded)
            }

            val textView = binding.proxySetSelected.editText as MaterialAutoCompleteTextView
            isProgrammaticChange = true
            textView.setText(data.proxySet.selected, false)
            isProgrammaticChange = false

            binding.proxySetSelected.hint = data.proxySet.selected
            binding.proxySetSelected.isEnabled = data.proxySet.selectable
            if (data.proxySet.selectable) {
                textView.setSimpleItems(data.proxySet.items.map { it.tag }.toTypedArray())
            }

            if (data.isExpanded) {
                populateItems(data)
            } else {
                binding.itemList.removeAllViews()
            }
        }

        private fun populateItems(data: ProxySetData) {
            binding.itemList.removeAllViews()
            val inflater = LayoutInflater.from(binding.root.context)

            data.proxySet.items.forEach { item ->
                val itemBinding = ViewProxySetItemBinding.inflate(inflater, binding.itemList, false)

                itemBinding.itemName.text = item.tag
                itemBinding.itemType.text = item.type
                val delay = data.delays[item.tag] ?: -1
                itemBinding.itemStatus.isVisible = delay >= 0
                if (delay >= 0) {
                    itemBinding.itemStatus.text = "${delay}ms"
                    itemBinding.itemStatus.setTextColor(
                        colorForURLTestDelay(binding.root.context, delay)
                    )
                }

                itemBinding.selectedView.isInvisible = data.proxySet.selected != item.tag

                if (data.proxySet.selectable) {
                    itemBinding.itemCard.setOnClickListener {
                        onSelectItem(data.proxySet.tag, item.tag)
                    }
                } else {
                    itemBinding.itemCard.setOnClickListener(null)
                }

                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                binding.itemList.addView(itemBinding.root, params)
            }
        }
    }
}