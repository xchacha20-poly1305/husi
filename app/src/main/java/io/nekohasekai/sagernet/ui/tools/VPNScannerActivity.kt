package io.nekohasekai.sagernet.ui.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutToolsVpnScannerBinding
import io.nekohasekai.sagernet.databinding.ViewVpnAppItemBinding
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.launch

class VPNScannerActivity : ThemedActivity() {

    private lateinit var binding: LayoutToolsVpnScannerBinding
    private val viewModel by viewModels<VPNScannerActivityViewModel>()
    private lateinit var adapter: VpnAppAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = LayoutToolsVpnScannerBinding.inflate(layoutInflater)
        this.binding = binding
        setContentView(binding.root)

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

        binding.toolbar.setContent {
            @Suppress("DEPRECATION")
            Mdc3Theme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val isScanning = uiState.progress != null
                TopAppBar(
                    title = { Text(stringResource(R.string.scan_vpn_app)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            onBackPressedDispatcher.onBackPressed()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.scanVPN()
                            },
                            enabled = !isScanning,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cached,
                                contentDescription = stringResource(R.string.refresh),
                            )
                        }
                    },
                )
            }
        }

        binding.scanVPNResult.adapter = VpnAppAdapter().also {
            adapter = it
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUIState)
            }
        }
    }

    private fun handleUIState(state: VPNScannerUiState) {
        if (state.progress == null) {
            binding.scanVPNProgress.isVisible = false
        } else {
            binding.scanVPNProgress.isVisible = true
            binding.scanVPNProgress.setProgressCompat(state.progress, true)
        }
        adapter.submitList(state.appInfos) {
            if (state.appInfos.isNotEmpty()) {
                binding.scanVPNResult.scrollToPosition(state.appInfos.size - 1)
            }
        }
    }

    private class VpnAppAdapter : ListAdapter<AppInfo, Holder>(AppInfoDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                ViewVpnAppItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class AppInfoDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(old: AppInfo, new: AppInfo): Boolean {
            return old.packageInfo.packageName == new.packageInfo.packageName
        }

        override fun areContentsTheSame(old: AppInfo, new: AppInfo): Boolean {
            return true
        }
    }

    private class Holder(private val binding: ViewVpnAppItemBinding) :
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