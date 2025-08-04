package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.setPadding
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
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.LayoutGroupItemBinding
import io.nekohasekai.sagernet.fmt.toUniversalLink
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.showAllowingStateLoss
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.widget.QRCodeDialog
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

class GroupFragment : ToolbarFragment(R.layout.layout_group),
    Toolbar.OnMenuItemClickListener {

    private val viewModel: GroupFragmentViewModel by viewModels()
    lateinit var groupListView: RecyclerView
    lateinit var groupAdapter: GroupAdapter
    lateinit var undoManager: UndoSnackbarManager<ProxyGroup>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setTitle(R.string.menu_group)
        toolbar.inflateMenu(R.menu.add_group_menu)
        toolbar.setOnMenuItemClickListener(this)

        groupListView = view.findViewById(R.id.group_list)
        ViewCompat.setOnApplyWindowInsetsListener(groupListView) { v, insets ->
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
        GroupManager.addListener(viewModel)
        groupListView.adapter = GroupAdapter().also {
            groupAdapter = it
        }

        undoManager = UndoSnackbarManager(requireActivity() as ThemedActivity, viewModel)

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START
        ) {
            override fun getSwipeDirs(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
            ): Int {
                val proxyGroup = (viewHolder as GroupHolder).group
                if (proxyGroup.ungrouped || proxyGroup.id in GroupUpdater.updating) {
                    return 0
                }
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun getDragDirs(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
            ): Int {
                val proxyGroup = (viewHolder as GroupHolder).group
                if (proxyGroup.ungrouped || proxyGroup.id in GroupUpdater.updating) {
                    return 0
                }
                return super.getDragDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val index = viewHolder.bindingAdapterPosition
                undoManager.remove(index to (viewHolder as GroupHolder).group)
                viewModel.fakeRemove(index)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
            ): Boolean {
                viewModel.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) {
                super.clearView(recyclerView, viewHolder)
                viewModel.commitMove(groupAdapter.currentList)
            }
        }).attachToRecyclerView(groupListView)

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

    private fun handleUiState(state: GroupUiState) {
        groupAdapter.submitList(state.groups)
    }

    private fun handleUiEvent(event: GroupEvents) {
        when (event) {
            GroupEvents.FlushUndoManager -> undoManager.flush()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new_group -> {
                startActivity(Intent(context, GroupSettingsActivity::class.java))
            }

            R.id.action_update_all -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.confirm)
                    .setMessage(R.string.update_all_subscription)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        viewModel.doUpdateAll()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        return true
    }

    private val exportProfiles =
        registerForActivityResult(CreateDocument("text/plain")) { data ->
            if (data != null) {
                lifecycleScope.launch {
                    val profiles = SagerDatabase.proxyDao.getByGroup(
                        viewModel.exportingGroup.value!!.id
                    )
                    viewModel.clearGroupExport()
                    val links = profiles.joinToString("\n") { it.toStdLink() }
                    try {
                        (requireActivity() as MainActivity).contentResolver.openOutputStream(
                            data
                        )!!.bufferedWriter().use {
                            it.write(links)
                        }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }

                }
            }
        }

    inner class GroupAdapter : ListAdapter<GroupItemUiState, GroupHolder>(GroupItemDiffCallback) {

        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupHolder {
            return GroupHolder(LayoutGroupItemBinding.inflate(layoutInflater, parent, false))
        }

        override fun onBindViewHolder(holder: GroupHolder, position: Int) {
            holder.bind(getItem(position))
        }

        override fun getItemId(position: Int): Long {
            return getItem(position).group.id
        }

    }

    override fun onDestroy() {
        if (::groupAdapter.isInitialized) {
            GroupManager.removeListener(viewModel)
        }

        super.onDestroy()

        if (!::undoManager.isInitialized) return
        undoManager.flush()
    }

    private object GroupItemDiffCallback : DiffUtil.ItemCallback<GroupItemUiState>() {
        override fun areItemsTheSame(old: GroupItemUiState, new: GroupItemUiState): Boolean {
            return old.group.id == new.group.id
        }

        override fun areContentsTheSame(old: GroupItemUiState, new: GroupItemUiState): Boolean {
            return old == new
        }

    }

    inner class GroupHolder(binding: LayoutGroupItemBinding) :
        RecyclerView.ViewHolder(binding.root),
        PopupMenu.OnMenuItemClickListener {

        lateinit var group: ProxyGroup
        val groupName = binding.groupName
        val groupStatus = binding.groupStatus
        val groupTraffic = binding.groupTraffic
        val groupUser = binding.groupUser
        val editButton = binding.edit
        val optionsButton = binding.options
        val updateButton = binding.groupUpdate
        val subscriptionUpdateProgress = binding.subscriptionUpdateProgress

        override fun onMenuItemClick(item: MenuItem): Boolean {

            fun export(link: String) {
                val success = SagerNet.trySetPrimaryClip(link)
                snackbar(
                    if (success) {
                        R.string.action_export_msg
                    } else {
                        R.string.action_export_err
                    }
                ).show()
            }

            when (item.itemId) {
                R.id.action_standard_clipboard -> {
                    group.subscription!!.link.blankAsNull()?.let {
                        export(it)
                    } ?: snackbar(androidx.preference.R.string.not_set).show()
                }

                R.id.action_standard_qr -> group.subscription!!.link.blankAsNull()?.let {
                    QRCodeDialog(it, group.displayName())
                        .showAllowingStateLoss(parentFragmentManager)
                } ?: snackbar(androidx.preference.R.string.not_set).show()

                R.id.action_universal_qr -> QRCodeDialog(
                    group.toUniversalLink(),
                    group.displayName(),
                ).showAllowingStateLoss(parentFragmentManager)

                R.id.action_universal_clipboard -> export(group.toUniversalLink())

                R.id.action_export_clipboard -> runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(group.id)
                    val links = profiles.joinToString("\n") { it.toStdLink() }
                    onMainDispatcher {
                        SagerNet.trySetPrimaryClip(links)
                        snackbar(getString(androidx.browser.R.string.copy_toast_msg)).show()
                    }
                }

                R.id.action_export_file -> {
                    viewModel.prepareGroupForExport(group)
                    startFilesForResult(exportProfiles, "profiles_${group.displayName()}.txt")
                }

                R.id.action_clear -> MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                    .setMessage(R.string.clear_profiles_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        viewModel.clearGroup(group.id)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }

            return true
        }


        fun bind(state: GroupItemUiState) {
            group = state.group

            itemView.setOnClickListener { }

            editButton.isGone = group.ungrouped
            updateButton.isInvisible = group.type != GroupType.SUBSCRIPTION
            groupName.text = group.displayName()

            editButton.setOnClickListener {
                startActivity(Intent(it.context, GroupSettingsActivity::class.java).apply {
                    putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, group.id)
                })
            }

            updateButton.setOnClickListener {
                viewModel.doUpdate(group)
            }

            optionsButton.setOnClickListener {
                val popup = PopupMenu(requireContext(), it)
                popup.menuInflater.inflate(R.menu.group_action_menu, popup.menu)

                if (group.type != GroupType.SUBSCRIPTION) {
                    popup.menu.let { menu ->
                        menu.removeItem(R.id.action_share_subscription)
                        menu.removeItem(R.id.action_share_subscription_universe)
                    }
                }
                popup.setOnMenuItemClickListener(this)
                popup.show()
            }

            if (state.isUpdating) {
                (groupName.parent as LinearLayout).apply {
                    setPadding(paddingLeft, dp2px(11), paddingRight, paddingBottom)
                }

                subscriptionUpdateProgress.isVisible = true
                subscriptionUpdateProgress.isIndeterminate = true

                updateButton.isInvisible = true
                editButton.isGone = true
            } else {
                (groupName.parent as LinearLayout).apply {
                    setPadding(paddingLeft, dp2px(15), paddingRight, paddingBottom)
                }

                subscriptionUpdateProgress.isVisible = false
                updateButton.isInvisible = group.type != GroupType.SUBSCRIPTION
                editButton.isGone = group.ungrouped
            }

            val subscription = group.subscription
            val context = requireContext()
            if (subscription != null &&
                (subscription.bytesUsed > 0L || subscription.bytesRemaining > 0)
            ) {
                val builder = StringBuilder().apply {
                    append(
                        if (subscription.bytesRemaining > 0L) {
                            context.getString(
                                R.string.subscription_traffic,
                                Formatter.formatFileSize(context, subscription.bytesUsed),
                                Formatter.formatFileSize(context, subscription.bytesRemaining),
                            )
                        } else {
                            context.getString(
                                R.string.subscription_used, Formatter.formatFileSize(
                                    context, subscription.bytesUsed
                                )
                            )
                        }
                    )
                    if (subscription.expiryDate > 0) append(
                        "\n" + getString(
                            R.string.subscription_expire,
                            @SuppressLint("SimpleDateFormat") // TODO: time zone
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(subscription.expiryDate * 1000L)),
                        )
                    )
                }

                val text = builder.toString()
                if (text.isNotBlank()) {
                    groupTraffic.isVisible = true
                    groupTraffic.text = text
                    groupStatus.setPadding(0)

                    // Show traffic used percent by progress.
                    if (!state.isUpdating && state.updateProgress != null) {
                        subscriptionUpdateProgress.apply {
                            isVisible = true
                            setProgressCompat(
                                state.updateProgress.progress,
                                true,
                            )
                        }
                    }
                }
            } else {
                groupTraffic.isVisible = false
                groupStatus.setPadding(0, 0, 0, dp2px(4))
            }

            groupUser.text = subscription?.username ?: ""

            @Suppress("DEPRECATION") when (state.group.type) {
                GroupType.BASIC -> {
                    if (state.counts == 0L) {
                        groupStatus.setText(R.string.group_status_empty)
                    } else {
                        groupStatus.text = getString(R.string.group_status_proxies, state.counts)
                    }
                }

                GroupType.SUBSCRIPTION -> {
                    groupStatus.text = if (state.counts == 0L) {
                        getString(R.string.group_status_empty_subscription)
                    } else {
                        val date = Date(state.group.subscription!!.lastUpdated * 1000L)
                        getString(
                            R.string.group_status_proxies_subscription,
                            state.counts,
                            "${date.month + 1} - ${date.date}"
                        )
                    }

                }
            }
        }
    }
}
