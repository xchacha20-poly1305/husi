package io.nekohasekai.sagernet.ui.dashboard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.databinding.LayoutStatusBinding
import io.nekohasekai.sagernet.databinding.ViewClashModeBinding
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ui.MainActivity
import java.net.Inet4Address
import java.net.Inet6Address

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
            insets
        }

        val (ipv4, ipv6) = getLocalAddresses()
        binding.ipv4AddressText.text = ipv4.toString()
        if (ipv4 != null) {
            binding.ipv4AddressText.setOnClickListener {
                snackbar(
                    if (SagerNet.trySetPrimaryClip(ipv4)) {
                        R.string.copy_success
                    } else {
                        R.string.copy_failed
                    }
                ).show()
            }
            binding.ipv4AddressText.setOnLongClickListener { view ->
                view.performClick()
            }
        } else {
            binding.ipv4AddressText.setTextIsSelectable(true)
        }
        binding.ipv6AddressText.text = ipv6.toString()
        if (ipv6 != null) {
            binding.ipv6AddressText.setOnClickListener {
                snackbar(
                    if (SagerNet.trySetPrimaryClip(ipv6)) {
                        R.string.copy_success
                    } else {
                        R.string.copy_failed
                    }
                ).show()
            }
            binding.ipv6AddressText.setOnLongClickListener {  view ->
                view.performClick()
            }
        } else {
            binding.ipv6AddressText.setTextIsSelectable(true)
        }

        val service = (requireActivity() as MainActivity).connection.service
        val clashModes = service?.clashModes ?: emptyList()
        val selected = service?.clashMode ?: RuleEntity.MODE_RULE
        binding.clashModeList.adapter = ClashModeAdapter(clashModes, selected).also {
            adapter = it
        }
    }

    suspend fun emitStats(memory: Long, goroutines: Int) {
        onMainDispatcher {
            binding.memoryText.text = Formatter.formatFileSize(binding.memoryText.context, memory)
            binding.goroutinesText.text = goroutines.toString()
        }
    }

    fun refreshClashMode() {
        val service = (requireActivity() as MainActivity).connection.service
        adapter.items = service?.clashModes ?: emptyList()
        adapter.selected = service?.clashMode ?: RuleEntity.MODE_RULE
        runOnMainDispatcher {
            adapter.notifyDataSetChanged()
        }
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
                    (requireActivity() as MainActivity).connection.service?.setClashMode(item)
                    updateSelected()
                }
            }
        }
    }

    /**
     * IPv4 + IPv6
     * @see <a href="https://github.com/chen08209/FlClash/blob/adb890d7637c2d6d10e7034b3599be7eacbfee99/lib/common/utils.dart#L304-L328">FlClash</a>
     * */
    private fun getLocalAddresses(): Pair<String?, String?> {
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        var wifiIPv4: String? = null
        var wifiIPv6: String? = null
        var cellularIPv4: String? = null
        var cellularIPv6: String? = null

        @Suppress("DEPRECATION")
        connectivityManager.allNetworks.forEach { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@forEach
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return@forEach

            linkProperties.linkAddresses.forEach { linkAddress ->
                val address = linkAddress.address
                if (!address.isLoopbackAddress) {
                    if (address is Inet4Address) {
                        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            if (wifiIPv4 == null) wifiIPv4 = address.hostAddress
                        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            if (cellularIPv4 == null) cellularIPv4 = address.hostAddress
                        }
                    } else if (address is Inet6Address && !address.isLinkLocalAddress) {
                        val hostAddress = address.hostAddress?.substringBefore('%')
                        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            if (wifiIPv6 == null) wifiIPv6 = hostAddress
                        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                            if (cellularIPv6 == null) cellularIPv6 = hostAddress
                        }
                    }
                }
            }
        }

        val finalIPv4 = wifiIPv4 ?: cellularIPv4
        val finalIPv6 = wifiIPv6 ?: cellularIPv6

        return Pair(finalIPv4, finalIPv6)
    }
}