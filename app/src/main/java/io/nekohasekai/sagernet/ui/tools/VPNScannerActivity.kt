package io.nekohasekai.sagernet.ui.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutVpnScannerBinding
import io.nekohasekai.sagernet.databinding.ViewVpnAppItemBinding
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlin.math.roundToInt

class VPNScannerActivity : ThemedActivity() {

    private lateinit var binding: LayoutVpnScannerBinding
    private lateinit var toolbar: Toolbar
    private var refresh: MenuItem? = null
    private val viewModel: VPNScannerActivityViewModel by viewModels()
    private lateinit var adapter: VpnAppAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = LayoutVpnScannerBinding.inflate(layoutInflater)
        this.binding = binding
        setContentView(binding.root)

        toolbar = findViewById(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = bars.top,
                left = bars.left,
                right = bars.right,
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.scanVPNResult) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setTitle(R.string.scan_vpn_app)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        binding.scanVPNResult.adapter = VpnAppAdapter().also {
            adapter = it
        }

        viewModel.uiState.observe(this, ::handleUIState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.refresh_menu, menu)
        refresh = menu.findItem(R.id.action_refresh)
        handleUIState(viewModel.uiState.value ?: VPNScannerUiState.Idle)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> {
            viewModel.scanVPN()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun handleUIState(state: VPNScannerUiState) {
        when (state) {
            VPNScannerUiState.Idle -> {
                binding.scanVPNProgress.isVisible = false
                refresh?.isVisible = false
                viewModel.scanVPN()
            }

            is VPNScannerUiState.Doing -> {
                binding.scanVPNProgress.isVisible = true
                refresh?.isVisible = false

                if (state.all > 0) {
                    binding.scanVPNProgress.setProgressCompat(
                        ((state.appInfos.size + 1).toDouble() / state.all.toDouble() * 100).roundToInt(),
                        true,
                    )
                }
                adapter.submitList(state.appInfos) {
                    // submitList is not synchronous
                    binding.scanVPNResult.scrollToPosition(state.appInfos.size - 1)
                }
            }

            is VPNScannerUiState.Finished -> {
                binding.scanVPNProgress.isVisible = false
                refresh?.isVisible = true
                adapter.submitList(state.appInfos)
            }
        }
    }

    private class VpnAppAdapter : ListAdapter<AppInfo, Holder>(AppInfoDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                ViewVpnAppItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class AppInfoDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageInfo.packageName == newItem.packageInfo.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }

    private class Holder(
        private val binding: ViewVpnAppItemBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(info: AppInfo) {
            binding.appIcon.setImageDrawable(info.packageInfo.applicationInfo!!.loadIcon(binding.root.context.packageManager))
            binding.appName.text =
                info.packageInfo.applicationInfo!!.loadLabel(binding.root.context.packageManager)
            binding.packageName.text = info.packageInfo.packageName
            val appType = info.vpnType.appType
            if (appType != null) {
                binding.appTypeText.text = info.vpnType.appType
            } else {
                binding.appTypeText.setText(R.string.vpn_app_type_other)
            }
            val coreType = info.vpnType.coreType?.coreType
            if (coreType != null) {
                binding.coreTypeText.text = info.vpnType.coreType.coreType
            } else {
                binding.coreTypeText.setText(R.string.vpn_core_type_unknown)
            }
            val corePath = info.vpnType.coreType?.corePath.takeIf { !it.isNullOrBlank() }
            if (corePath != null) {
                binding.corePathLayout.isVisible = true
                binding.corePathText.text = corePath
            } else {
                binding.corePathLayout.isVisible = false
            }

            val goVersion = info.vpnType.coreType?.goVersion.takeIf { !it.isNullOrBlank() }
            if (goVersion != null) {
                binding.goVersionLayout.isVisible = true
                binding.goVersionText.text = goVersion
            } else {
                binding.goVersionLayout.isVisible = false
            }

            binding.root.setOnClickListener { view ->
                view.context.startActivity(
                    Intent()
                        .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(
                            Uri.fromParts(
                                "package", info.packageInfo.packageName, null
                            )
                        )
                )
            }
        }
    }
}