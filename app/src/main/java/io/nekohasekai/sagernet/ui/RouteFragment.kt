package io.nekohasekai.sagernet.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.RuleEntity.Companion.OUTBOUND_BLOCK
import io.nekohasekai.sagernet.database.RuleEntity.Companion.OUTBOUND_DIRECT
import io.nekohasekai.sagernet.database.RuleEntity.Companion.OUTBOUND_PROXY
import io.nekohasekai.sagernet.databinding.LayoutRouteBinding
import io.nekohasekai.sagernet.databinding.ViewRouteItemBinding
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.snackbarAdapter
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class RouteFragment : OnKeyDownFragment(R.layout.layout_route),
    ProfileManager.RuleListener {

    private lateinit var binding: LayoutRouteBinding
    private val viewModel by viewModels<RouteFragmentViewModel>()
    private lateinit var ruleAdapter: RuleAdapter
    private lateinit var undoManager: UndoSnackbarManager<RuleEntity>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutRouteBinding.bind(view)
        binding.toolbar.setContent {
            @Suppress("DEPRECATION")
            AppTheme {
                var menuExpanded by remember { mutableStateOf(false) }
                TopAppBar(
                    title = { Text(stringResource(R.string.menu_route)) },
                    navigationIcon = {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.menu),
                            contentDescription = stringResource(R.string.menu),
                        ) {
                            (requireActivity() as MainActivity).binding
                                .drawerLayout.openDrawer(GravityCompat.START)
                        }
                    },
                    actions = {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.add_road),
                            contentDescription = stringResource(R.string.route_add),
                        ) {
                            startActivity(
                                Intent(
                                    requireContext(),
                                    RouteSettingsActivity::class.java
                                )
                            )
                        }
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.more_vert),
                            contentDescription = stringResource(R.string.more),
                            onClick = { menuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.route_reset)) },
                                onClick = {
                                    menuExpanded = false
                                    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                                        .setMessage(R.string.clear_profiles_message)
                                        .setPositiveButton(android.R.string.ok) { _, _ ->
                                            lifecycleScope.launch {
                                                viewModel.reset()
                                            }
                                        }
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .show()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.route_manage_assets)) },
                                onClick = {
                                    menuExpanded = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            AssetsActivity::class.java
                                        )
                                    )
                                }
                            )
                        }
                    },
                )
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.routeList) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(4),
                right = bars.right + dp2px(4),
                bottom = bars.bottom + dp2px(64),
            )
            insets
        }

        binding.routeIntroduce.setOnClickListener { view ->
            view.context.launchCustomTab("https://github.com/xchacha20-poly1305/husi/wiki/Route")
        }

        ProfileManager.addListener(this)
        binding.routeList.adapter = RuleAdapter {
            viewModel.updateRule(it)
        }.also {
            ruleAdapter = it
        }
        undoManager = UndoSnackbarManager(
            (requireActivity() as ThemedActivity).snackbarAdapter,
            viewModel,
        )

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.START,
        ) {

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val index = viewHolder.bindingAdapterPosition
                lifecycleScope.launch {
                    viewModel.undoableRemove(index)
                    undoManager.remove(index to (viewHolder as RuleHolder).rule)
                }
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                lifecycleScope.launch {
                    viewModel.fakeMove(from, to)
                }
                return true
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) {
                super.clearView(recyclerView, viewHolder)
                viewModel.commitMove(ruleAdapter.currentList)
            }
        }).attachToRecyclerView(binding.routeList)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect(::handleUiEvent)
            }
        }
    }

    private fun handleUiState(state: RouteFragmentUiState) {
        ruleAdapter.submitList(state.rules)
    }

    private fun handleUiEvent(event: RouteFragmentUiEvent) {
        when (event) {
            RouteFragmentUiEvent.NeedReload -> needReload()
        }
    }

    override fun onDestroy() {
        ProfileManager.removeListener(this)
        super.onDestroy()

        if (::undoManager.isInitialized) {
            undoManager.flush()
        }
    }

    override suspend fun onAdd(rule: RuleEntity) {
        viewModel.onAdd(rule)
    }

    override suspend fun onUpdated(rule: RuleEntity) {
        viewModel.onUpdated(rule)
    }

    override suspend fun onRemoved(ruleId: Long) {
        viewModel.onRemoved(ruleId)
    }

    override suspend fun onCleared() {
        viewModel.onRulesCleared()
    }

    private class RuleAdapter(val updateRule: (RuleEntity) -> Unit) :
        ListAdapter<RuleEntity, RuleHolder>(RuleEntityDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleHolder {
            return RuleHolder(
                ViewRouteItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                ),
                updateRule,
            )
        }

        override fun onBindViewHolder(holder: RuleHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class RuleEntityDiffCallback : DiffUtil.ItemCallback<RuleEntity>() {
        override fun areItemsTheSame(old: RuleEntity, new: RuleEntity): Boolean {
            return old.id == new.id
        }

        override fun areContentsTheSame(old: RuleEntity, new: RuleEntity): Boolean {
            return old == new
        }
    }

    private class RuleHolder(binding: ViewRouteItemBinding, val updateRule: (RuleEntity) -> Unit) :
        RecyclerView.ViewHolder(binding.root) {

        lateinit var rule: RuleEntity
        private val routeName = binding.routeName
        private val ruleSummary = binding.ruleSummary
        private val routeAction = binding.routeAction
        private val editButton = binding.edit
        private val enableSwitch = binding.enable

        fun bind(ruleEntity: RuleEntity) {
            rule = ruleEntity
            routeName.text = rule.displayName()
            ruleSummary.text = rule.summary(ruleSummary.context)
            routeAction.text = when (rule.action) {
                "", SingBoxOptions.ACTION_ROUTE -> rule.displayOutbound(routeAction.context)
                else -> "action: ${rule.action}"
            }
            itemView.setOnClickListener {
                enableSwitch.performClick()
            }
            enableSwitch.isChecked = rule.enabled
            enableSwitch.setOnCheckedChangeListener { view, isChecked ->
                rule.enabled = isChecked
                updateRule(rule)
            }
            editButton.setOnClickListener { view ->
                view.context.startActivity(
                    Intent(
                        view.context,
                        RouteSettingsActivity::class.java,
                    ).putExtra(RouteSettingsActivity.EXTRA_ROUTE_ID, rule.id)
                )
            }
        }

        private fun RuleEntity.summary(context: Context): String {
            var summary = ""
            if (domains.isNotBlank()) summary += "$domains\n"
            if (ip.isNotBlank()) summary += "$ip\n"
            if (source.isNotBlank()) summary += "source: $source\n"
            if (sourcePort.isNotBlank()) summary += "sourcePort: $sourcePort\n"
            if (port.isNotBlank()) summary += "port: $port\n"
            if (network.isNotEmpty()) summary += "network: $network\n"
            if (protocol.isNotEmpty()) summary += "protocol: $protocol\n"
            if (clientType.isNotEmpty()) summary += "client: $clientType\n"
            if (packages.isNotEmpty()) summary += context.getString(
                R.string.apps_message, packages.size
            ) + "\n"
            if (ssid.isNotBlank()) summary += "ssid: $ssid\n"
            if (bssid.isNotBlank()) summary += "bssid: $bssid\n"
            if (clashMode.isNotBlank()) summary += "clashMode: $clashMode\n"
            if (networkType.isNotEmpty()) summary += "networkType: $networkType\n"
            if (networkIsExpensive) summary += "networkIsExpensive\n"
            if (networkInterfaceAddress.isNotEmpty()) summary += "networkInterfaceAddress: $networkInterfaceAddress\n"

            if (overrideAddress.isNotBlank()) summary += "overrideAddress: $overrideAddress\n"
            if (overridePort > 0) summary += "overridePort: $overridePort\n"
            if (tlsFragment) {
                summary += "TLS fragment\n"
                if (tlsFragmentFallbackDelay.isNotBlank()) {
                    summary += "tlsFragmentFallbackDelay: $tlsFragmentFallbackDelay\n"
                }
            }
            if (tlsRecordFragment) {
                summary += "TLS record fragment\n"
            }

            if (resolveStrategy.isNotBlank()) summary += "resolveStrategy: $resolveStrategy\n"
            if (resolveDisableCache) summary += "resolveDisableCache\n"
            if (resolveRewriteTTL >= 0) summary += "resolveRewriteTTL: $resolveRewriteTTL\n"
            if (resolveClientSubnet.isNotBlank()) summary += "resolveClientSubnet: $resolveClientSubnet\n"

            if (sniffTimeout.isNotBlank()) summary += "sniffTimeout: $sniffTimeout\n"
            if (sniffers.isNotEmpty()) summary += "sniffers: $sniffers\n"

            if (customConfig.isNotBlank()) summary += context.getString(R.string.menu_route) + "\n"
            if (customDnsConfig.isNotBlank()) summary += context.getString(R.string.cag_dns) + "\n"

            // Even has "\n" suffix, TextView's "..." will be added and remove the last "\n".
            val lines = summary.trim().split("\n")
            return if (lines.size > 5) {
                lines.subList(0, 5).joinToString("\n", postfix = "\n...")
            } else {
                summary.trim()
            }
        }

        private fun RuleEntity.displayOutbound(context: Context): String {
            return when (outbound) {
                OUTBOUND_PROXY -> context.getString(R.string.route_proxy)
                OUTBOUND_DIRECT -> context.getString(R.string.route_bypass)
                OUTBOUND_BLOCK -> context.getString(R.string.route_block)
                else -> ProfileManager.getProfile(outbound)?.displayName()
                    ?: context.getString(R.string.error_title)
            }
        }
    }
}
