package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.findFragment
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
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.RuleEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.LayoutRouteBinding
import io.nekohasekai.sagernet.databinding.ViewRouteItemBinding
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.launchCustomTab
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.launch

class RouteFragment : ToolbarFragment(R.layout.layout_route),
    Toolbar.OnMenuItemClickListener, ProfileManager.RuleListener {

    private lateinit var binding: LayoutRouteBinding
    private val viewModel by viewModels<RouteFragmentViewModel>()
    private lateinit var ruleAdapter: RuleAdapter
    private lateinit var undoManager: UndoSnackbarManager<RuleEntity>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setTitle(R.string.menu_route)
        toolbar.inflateMenu(R.menu.add_route_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutRouteBinding.bind(view)
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
        undoManager = UndoSnackbarManager(requireActivity() as ThemedActivity, viewModel)

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

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new_route -> {
                startActivity(Intent(requireContext(), RouteSettingsActivity::class.java))
            }

            R.id.action_reset_route -> {
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

            R.id.action_manage_assets -> {
                startActivity(Intent(requireContext(), AssetsActivity::class.java))
            }
        }
        return true
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

    private class RuleHolder(val binding: ViewRouteItemBinding, val updateRule: (RuleEntity)->Unit) :
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
            ruleSummary.text = rule.mkSummary()
            routeAction.text = when (rule.action) {
                "", SingBoxOptions.ACTION_ROUTE -> rule.displayOutbound()
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
    }
}
