package io.nekohasekai.sagernet.ui.dashboard

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.databinding.LayoutStatusBinding
import io.nekohasekai.sagernet.databinding.ViewClashModeBinding
import io.nekohasekai.sagernet.databinding.ViewNetworkInterfaceBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ui.MainActivity
import kotlinx.coroutines.launch

class StatusFragment : Fragment(R.layout.layout_status) {

    private lateinit var binding: LayoutStatusBinding
    private lateinit var clashModeAdapter: ClashModeAdapter
    private lateinit var networkInterfaceAdapter: NetworkInterfaceAdapter
    private val viewModel by viewModels<StatusFragmentViewModel>()

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
            insets
        }

        binding.ipv4AddressText.setOnClickListener { view ->
            snackbar(
                if (SagerNet.trySetPrimaryClip((view as TextView).text.toString())) {
                    R.string.copy_success
                } else {
                    R.string.copy_failed
                }
            ).show()
        }
        binding.ipv4AddressText.setOnLongClickListener { view ->
            view.performClick()
        }

        binding.ipv6AddressText.setOnClickListener { view ->
            snackbar(
                if (SagerNet.trySetPrimaryClip((view as TextView).text.toString())) {
                    R.string.copy_success
                } else {
                    R.string.copy_failed
                }
            ).show()
        }
        binding.ipv6AddressText.setOnLongClickListener { view ->
            view.performClick()
        }

        binding.clashModeList.adapter = ClashModeAdapter {
            (requireActivity() as MainActivity).connection.service?.clashMode = it
        }.also {
            clashModeAdapter = it
        }
        binding.networkInterfaceList.adapter = NetworkInterfaceAdapter().also {
            networkInterfaceAdapter = it
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.initialize((requireActivity() as MainActivity).connection.service!!)
    }

    private suspend fun handleUiState(state: StatusFragmentUiState) {
        val items = state.clashModes.mapX {
            ClashModeItem(it, it == state.selectedClashMode)
        }

        binding.memoryText.text = Formatter.formatFileSize(
            binding.memoryText.context,
            state.memory,
        )
        binding.goroutinesText.text = state.goroutines.toString()

        binding.ipv4AddressText.text = state.ipv4 ?: getString(R.string.no_statistics)
        binding.ipv6AddressText.text = state.ipv6 ?: getString(R.string.no_statistics)

        clashModeAdapter.submitList(items)
        networkInterfaceAdapter.submitList(state.networkInterfaces)
    }

    override fun onPause() {
        viewModel.stop()
        super.onPause()
    }

    private data class ClashModeItem(
        val name: String,
        val isSelected: Boolean,
    )

    private inner class ClashModeAdapter(val onClick: (String) -> Unit) :
        ListAdapter<ClashModeItem, ClashModeItemView>(clashModeDiffCallback) {
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


        override fun onBindViewHolder(holder: ClashModeItemView, position: Int) {
            val instance = getItem(position)
            holder.bind(instance, onClick)
        }
    }

    private val clashModeDiffCallback = object : DiffUtil.ItemCallback<ClashModeItem>() {
        override fun areItemsTheSame(old: ClashModeItem, new: ClashModeItem): Boolean {
            return old.name == new.name
        }

        override fun areContentsTheSame(old: ClashModeItem, new: ClashModeItem): Boolean {
            // Already checked name in areItemsTheSame
            return old.isSelected == new.isSelected
        }

    }

    private class ClashModeItemView(val binding: ViewClashModeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(instance: ClashModeItem, onClick: (mode: String) -> Unit) {
            binding.clashModeButtonText.text = instance.name
            if (instance.isSelected) {
                binding.clashModeButtonText.setTextColor(
                    binding.root.context.getColorAttr(com.google.android.material.R.attr.colorOnPrimary)
                )
                binding.clashModeButton.setBackgroundResource(R.drawable.bg_rounded_rectangle_active)
                binding.clashModeButton.isClickable = false
            } else {
                binding.clashModeButtonText.setTextColor(
                    binding.root.context.getColorAttr(com.google.android.material.R.attr.colorOnPrimaryContainer)
                )
                binding.clashModeButton.setBackgroundResource(R.drawable.bg_rounded_rectangle)
                binding.clashModeButton.setOnClickListener {
                    onClick(instance.name)
                }
            }
        }
    }

    private class NetworkInterfaceAdapter() :
        ListAdapter<NetworkInterfaceInfo, NetworkInterfaceHolder>(NetworkInterfaceDiffUtil) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NetworkInterfaceHolder {
            return NetworkInterfaceHolder(
                ViewNetworkInterfaceBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
        }

        override fun onBindViewHolder(holder: NetworkInterfaceHolder, position: Int) {
            holder.bind(getItem(position))
        }

    }

    private object NetworkInterfaceDiffUtil : DiffUtil.ItemCallback<NetworkInterfaceInfo>() {
        override fun areItemsTheSame(
            old: NetworkInterfaceInfo,
            new: NetworkInterfaceInfo,
        ): Boolean {
            return old.name == new.name
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(
            old: NetworkInterfaceInfo,
            new: NetworkInterfaceInfo,
        ): Boolean {
            return old.addresses == new.addresses
        }
    }

    private class NetworkInterfaceHolder(val binding: ViewNetworkInterfaceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(info: NetworkInterfaceInfo) {
            binding.interfaceName.text = info.name
            binding.interfaceAddress.text = info.addresses.joinToString("\n")
        }
    }
}