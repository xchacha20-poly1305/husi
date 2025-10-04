package io.nekohasekai.sagernet.ui.configuration

import android.app.Activity
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.format.Formatter
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.getSystemService
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.displayType
import io.nekohasekai.sagernet.databinding.LayoutProfileBinding
import io.nekohasekai.sagernet.databinding.LayoutProfileListBinding
import io.nekohasekai.sagernet.fmt.config.ConfigBean
import io.nekohasekai.sagernet.fmt.toUniversalLink
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.ValidateResult
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.getColour
import io.nekohasekai.sagernet.ktx.isInsecure
import io.nekohasekai.sagernet.ktx.needReload
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.scrollTo
import io.nekohasekai.sagernet.ktx.showAllowingStateLoss
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ktx.readableUrlTestError
import io.nekohasekai.sagernet.ktx.trySetPrimaryClip
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.configuration.ConfigurationFragment.SelectCallback
import io.nekohasekai.sagernet.widget.QRCodeDialog
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.launch

class GroupProfilesHolder() : Fragment(R.layout.layout_profile_list) {

    companion object {
        private const val ARG_FOR_SELECT = "for_select"
        private const val ARG_GROUP = "group"
        private const val ARG_PRE_SELECTED = "pre_selected"
    }

    constructor(
        forSelect: Boolean,
        group: ProxyGroup,
        preSelected: Long?,
    ) : this() {
        arguments = bundleOf(
            ARG_FOR_SELECT to forSelect,
            ARG_GROUP to group,
            ARG_PRE_SELECTED to preSelected,
        )
    }

    private val viewModel by viewModels<GroupProfilesHolderViewModel>()
    private val parentViewModel by viewModels<ConfigurationFragmentViewModel>({ requireParentFragment() })
    private lateinit var binding: LayoutProfileListBinding
    private lateinit var undoManager: UndoSnackbarManager<ProfileItem>
    private lateinit var adapter: ConfigurationAdapter

    private val clipboard by lazy { requireContext().getSystemService<ClipboardManager>()!! }

    private val isEnabled: Boolean
        get() = DataStore.serviceState.let { it.canStop || it == BaseService.State.Stopped }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            viewModel.initialize(
                it.getBoolean(ARG_FOR_SELECT),
                BundleCompat.getParcelable(it, ARG_GROUP, ProxyGroup::class.java)!!,
                it.getLong(ARG_PRE_SELECTED).takeIf { it > 0L },
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutProfileListBinding.bind(view)
        ViewCompat.setOnApplyWindowInsetsListener(binding.configurationList) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(4),
                right = bars.right + dp2px(4),
                bottom = bars.bottom + dp2px(64),
            )
            insets
        }
        binding.configurationList.adapter = ConfigurationAdapter().also {
            adapter = it
        }
        binding.configurationList.setItemViewCacheSize(20)
        binding.configurationList.requestFocus()

        if (!viewModel.forSelect) {
            undoManager = UndoSnackbarManager(activity as MainActivity, viewModel)

            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START
            ) {
                override fun getSwipeDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int {
                    return 0
                }

                override fun getDragDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) = if (isEnabled) {
                    super.getDragDirs(recyclerView, viewHolder)
                } else {
                    0
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    viewModel.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                    return true
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) {
                    super.clearView(recyclerView, viewHolder)
                    viewModel.commitMove()
                }
            }).attachToRecyclerView(binding.configurationList)

        }

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

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parentViewModel.childEvent.collect(::handleChildEvent)
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                parentViewModel.searchQuery.collect {
                    viewModel.query = it
                }
            }
        }

        if (savedInstanceState != null) lifecycleScope.launch {
            viewModel.onProfileSelect(DataStore.selectedProxy)
        }
    }

    private fun handleUiState(state: GroupProfilesHolderUiState) {
        adapter.submitList(state.profiles) {
            state.scrollIndex?.let {
                binding.configurationList.scrollTo(it)
            }
        }
    }

    private fun handleUiEvent(event: GroupProfilesHolderUiEvent) = when (event) {
        is GroupProfilesHolderUiEvent.AlertForDelete -> {
            val context = requireContext()
            val message = context.resources.getQuantityString(
                R.plurals.delete_confirm_detail,
                event.size,
                event.summary,
            )
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.confirm)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    event.confirm()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun handleChildEvent(event: ConfigurationChildEvent) {
        if (event.group != viewModel.group.id) return
        when (event) {
            is ConfigurationChildEvent.ScrollToProxy -> {
                val index = adapter.currentList
                    .indexOfFirst { it.profile.id == event.id }
                    .takeIf { it >= 0 }
                    ?: if (event.fallbackToTop) 0 else return

                val layoutManager = binding.configurationList.layoutManager as LinearLayoutManager
                val first = layoutManager.findFirstVisibleItemPosition()
                val last = layoutManager.findLastVisibleItemPosition()
                if (index !in first..last) {
                    binding.configurationList.scrollTo(index)
                }
            }

            is ConfigurationChildEvent.RequestFocusIfNotHave -> {
                binding.configurationList.apply {
                    if (!hasFocus()) requestFocus()
                }
            }

            is ConfigurationChildEvent.ClearTrafficStatistic -> viewModel.clearTrafficStatistics()

            is ConfigurationChildEvent.ClearResult -> viewModel.clearResults()

            is ConfigurationChildEvent.DeleteUnavailable -> viewModel.deleteUnavailable()

            is ConfigurationChildEvent.RemoveDuplicate -> viewModel.removeDuplicate()

            is ConfigurationChildEvent.OnProfileSelect -> viewModel.onProfileSelect(event.new)

            is ConfigurationChildEvent.UpdateOrder -> viewModel.updateOrder(event.order)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (!::undoManager.isInitialized) return
        undoManager.flush()
    }


    override fun onResume() {
        super.onResume()

        checkOrderMenu()
        if (::binding.isInitialized) {
            binding.configurationList.requestFocus()
        }
    }

    private fun checkOrderMenu() {
        if (viewModel.forSelect) return

        val parentFragment = requireParentFragment() as ConfigurationFragment
        parentFragment.composeToolbar()
    }

    private inner class ConfigurationAdapter :
        ListAdapter<ProfileItem, ConfigurationHolder>(ProfileItemDiffCallback) {

        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigurationHolder {
            return ConfigurationHolder(
                LayoutProfileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
        }

        override fun getItemId(position: Int): Long {
            return getItem(position).profile.id
        }

        override fun onBindViewHolder(holder: ConfigurationHolder, position: Int) = try {
            holder.bind(getItem(position))
        } catch (_: NullPointerException) { // when group deleted
        }

        override fun onBindViewHolder(
            holder: ConfigurationHolder,
            position: Int,
            payloads: List<Any?>,
        ) = try {
            var mask = 0
            for (payload in payloads) {
                mask = mask or payload as Int
            }
            if (mask == 0) {
                super.onBindViewHolder(holder, position, payloads)
            } else {
                val item = getItem(position)
                if (mask and ProfileItemDiffCallback.PAYLOAD_BASE != 0) {
                    holder.bindBase(item.profile)
                }
                if (mask and ProfileItemDiffCallback.PAYLOAD_SELECTED != 0) {
                    holder.bindSelected(item.isSelected)
                }
                if (mask and ProfileItemDiffCallback.PAYLOAD_VALIDATE != 0) {
                    holder.bindShare(item.profile)
                }
                Unit
            }
        } catch (_: NullPointerException) {
        }

    }

    private object ProfileItemDiffCallback : DiffUtil.ItemCallback<ProfileItem>() {
        const val PAYLOAD_BASE = 1 shl 0 // name + address + traffic + status
        const val PAYLOAD_SELECTED = 1 shl 1
        const val PAYLOAD_VALIDATE = 1 shl 2

        override fun areItemsTheSame(old: ProfileItem, new: ProfileItem): Boolean {
            return old.profile.id == new.profile.id
        }

        override fun areContentsTheSame(old: ProfileItem, new: ProfileItem): Boolean {
            return old.profile == new.profile
                    && old.started == new.started
                    && old.isSelected == new.isSelected
        }

        override fun getChangePayload(old: ProfileItem, new: ProfileItem): Any? {
            var mask = 0
            if (old.profile != new.profile || old.started != new.started) {
                mask = mask or PAYLOAD_BASE
            }
            if (old.isSelected != new.isSelected) {
                mask = mask or PAYLOAD_SELECTED
            }
            val oldBean = old.profile.requireBean()
            val newBean = new.profile.requireBean()
            if (oldBean.isInsecure() != newBean.isInsecure()) {
                mask = mask or PAYLOAD_VALIDATE
            }
            return if (mask != 0) {
                mask
            } else {
                null
            }
        }

    }

    private inner class ConfigurationHolder(val binding: LayoutProfileBinding) :
        RecyclerView.ViewHolder(binding.root), PopupMenu.OnMenuItemClickListener {

        private lateinit var entity: ProxyEntity

        fun bind(profile: ProfileItem) {
            entity = profile.profile

            bindSelected(profile.isSelected)
            if (viewModel.forSelect) binding.root.setOnClickListener {
                (requireActivity() as SelectCallback).returnProfile(entity.id)
            } else binding.root.setOnClickListener {
                parentViewModel.onProfileSelect(entity.id)
            }

            bindBase(entity)

            binding.edit.setOnClickListener {
                viewModel.editingID = entity.id
                editProfileLauncher.launch(
                    entity.settingIntent(
                        it.context,
                        viewModel.group.type == GroupType.SUBSCRIPTION,
                    )
                )
            }

            binding.remove.setOnClickListener {
                val index = bindingAdapterPosition
                viewModel.fakeRemove(index)
                undoManager.remove(index to profile)
            }

            binding.share.isVisible = !viewModel.forSelect
            binding.edit.isVisible = !viewModel.forSelect
            binding.remove.isVisible = !viewModel.forSelect
            binding.remove.isEnabled = !profile.started
            binding.selectedView.isVisible = profile.isSelected

            bindShare(entity)
        }

        fun bindBase(entity: ProxyEntity) {
            this.entity = entity
            val bean = entity.requireBean()

            binding.profileType.text = entity.displayType(binding.profileType.context)

            val tx = entity.tx
            val rx = entity.rx
            val hasTraffic = tx + rx > 0L
            val trafficString = if (hasTraffic) {
                binding.root.context.getString(
                    R.string.traffic,
                    Formatter.formatFileSize(binding.root.context, tx),
                    Formatter.formatFileSize(binding.root.context, rx),
                )
            } else {
                null
            }

            var name: String
            var address: String? = null
            if (viewModel.blurredAddress) {
                if (bean.name.isNullOrBlank()) {
                    name = bean.displayAddress().blur()
                } else {
                    name = bean.displayName()
                    if (viewModel.alwaysShowAddress) {
                        address = bean.displayAddress().blur()
                    }
                }
            } else {
                name = bean.displayName()
                if (viewModel.alwaysShowAddress) {
                    address = bean.displayAddress()
                }
            }

            binding.profileName.text = name

            binding.profileAddress.apply {
                if (address == null) {
                    text = null
                    isVisible = false
                } else {
                    text = address
                    isVisible = true
                }
            }

            binding.trafficText.isVisible = hasTraffic
            if (hasTraffic) {
                binding.trafficText.text = trafficString
            }

            (binding.trafficText.parent as View).isGone =
                (!hasTraffic || entity.status <= ProxyEntity.STATUS_INITIAL) && address == null

            if (entity.status <= ProxyEntity.STATUS_INITIAL) {
                if (hasTraffic) {
                    binding.profileStatus.text = trafficString
                    binding.profileStatus.setTextColor(
                        binding.profileStatus.context.getColorAttr(
                            android.R.attr.textColorSecondary
                        )
                    )
                    binding.trafficText.text = ""
                } else {
                    binding.profileStatus.text = ""
                }
            } else if (entity.status == ProxyEntity.STATUS_AVAILABLE) {
                binding.profileStatus.text = binding.profileStatus.context.getString(
                    R.string.available,
                    entity.ping,
                )
                binding.profileStatus.setTextColor(binding.profileStatus.context.getColour(R.color.material_green_500))
            } else {
                binding.profileStatus.setTextColor(binding.profileStatus.context.getColour(R.color.material_red_500))
                if (entity.status == ProxyEntity.STATUS_UNREACHABLE) {
                    binding.profileStatus.text = entity.error
                }
            }

            if (entity.status == ProxyEntity.STATUS_UNAVAILABLE) {
                val err = entity.error
                val msg = readableUrlTestError(err)
                binding.profileStatus.text =
                    binding.profileStatus.context.getString(msg ?: R.string.unavailable)
                if (err != null) {
                    binding.profileStatus.setOnClickListener {
                        alert(err).show()
                    }
                }
            } else {
                binding.profileStatus.setOnClickListener(null)
            }
        }

        fun bindSelected(isSelected: Boolean) {
            binding.selectedView.isInvisible = !isSelected
        }

        fun bindShare(entity: ProxyEntity) {
            this.entity = entity

            fun showShare(anchor: View) {
                val popup = PopupMenu(anchor.context, anchor)
                popup.menuInflater.inflate(R.menu.profile_share_menu, popup.menu)

                if (!entity.haveStandardLink()) {
                    popup.menu.findItem(R.id.action_group_qr).subMenu?.removeItem(R.id.action_standard_qr)
                    popup.menu.findItem(R.id.action_group_clipboard).subMenu?.removeItem(R.id.action_standard_clipboard)
                }

                if (!entity.haveLink()) {
                    popup.menu.removeItem(R.id.action_group_qr)
                    popup.menu.removeItem(R.id.action_group_clipboard)
                }

                val bean = entity.requireBean()
                if (entity.type == ProxyEntity.TYPE_CHAIN ||
                    entity.type == ProxyEntity.TYPE_PROXY_SET ||
                    entity.mustUsePlugin() ||
                    (bean as? ConfigBean)?.type == ConfigBean.TYPE_CONFIG
                ) {
                    popup.menu.removeItem(R.id.action_group_outbound)
                }

                popup.setOnMenuItemClickListener(this@ConfigurationHolder)
                popup.show()
            }

            if (viewModel.forSelect) return

            val validateResult = if (viewModel.securityAdvisory) {
                entity.requireBean().isInsecure()
            } else {
                ValidateResult.Secure
            }
            when (validateResult) {
                is ValidateResult.Insecure -> {
                    binding.share.isVisible = true

                    binding.share.setBackgroundColor(Color.RED)
                    binding.share.setIconResource(R.drawable.ic_baseline_warning_24)
                    binding.share.setIconTint(ColorStateList.valueOf(Color.WHITE))

                    binding.share.setOnClickListener {
                        MaterialAlertDialogBuilder(it.context)
                            .setTitle(R.string.insecure)
                            .setMessage(
                                resources.openRawResource(validateResult.textRes)
                                    .bufferedReader().use { it.readText() })
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                showShare(it)
                            }.show().apply {
                                findViewById<TextView>(android.R.id.message)?.apply {
                                    Linkify.addLinks(this, Linkify.WEB_URLS)
                                    movementMethod = LinkMovementMethod.getInstance()
                                }
                            }
                    }
                }

                is ValidateResult.Deprecated -> {
                    binding.share.isVisible = true

                    binding.share.setBackgroundColor(Color.YELLOW)
                    binding.share.setIconResource(R.drawable.ic_baseline_warning_24)
                    binding.share.setIconTint(ColorStateList.valueOf(Color.GRAY))

                    binding.share.setOnClickListener {
                        MaterialAlertDialogBuilder(it.context)
                            .setTitle(R.string.deprecated)
                            .setMessage(
                                resources.openRawResource(validateResult.textRes)
                                    .bufferedReader().use { it.readText() })
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                showShare(it)
                            }.show().apply {
                                findViewById<TextView>(android.R.id.message)?.apply {
                                    Linkify.addLinks(this, Linkify.WEB_URLS)
                                    movementMethod = LinkMovementMethod.getInstance()
                                }
                            }
                    }
                }

                is ValidateResult.Secure -> {
                    binding.share.setBackgroundColor(Color.TRANSPARENT)
                    binding.share.setIconResource(R.drawable.ic_social_share)
                    binding.share.setIconTint(
                        ColorStateList.valueOf(
                            binding.share.context.getColorAttr(
                                com.google.android.material.R.attr.colorOnSurface
                            )
                        )
                    )
                    binding.share.isVisible = true

                    binding.share.setOnClickListener {
                        showShare(it)
                    }
                }
            }
        }

        private fun showCode(link: String, name: String) {
            QRCodeDialog(link, name).showAllowingStateLoss(parentFragmentManager)
        }

        private fun export(link: String) {
            val success = clipboard.trySetPrimaryClip(link)
            snackbar(
                if (success) {
                    R.string.action_export_msg
                } else {
                    R.string.action_export_err
                }
            ).show()
        }

        override fun onMenuItemClick(item: MenuItem) = try {
            when (item.itemId) {
                R.id.action_standard_qr -> {
                    showCode(entity.toStdLink(), entity.displayName())
                    true
                }

                R.id.action_standard_clipboard -> {
                    export(entity.toStdLink())
                    true
                }

                R.id.action_universal_qr -> {
                    showCode(entity.requireBean().toUniversalLink(), entity.displayName())
                    true
                }

                R.id.action_universal_clipboard -> {
                    export(entity.requireBean().toUniversalLink())
                    true
                }

                R.id.action_config_export_clipboard -> {
                    export(entity.exportConfig().first)
                    true
                }

                R.id.action_config_export_file -> {
                    val cfg = entity.exportConfig()
                    viewModel.exportConfig = cfg.first
                    startFilesForResult(
                        exportConfig, cfg.second
                    )
                    true
                }

                R.id.action_outbound_export_clipboard -> {
                    export(entity.exportOutbound())
                    true
                }

                else -> false
            }
        } catch (e: Exception) {
            Logs.w(e)
            snackbar(e.readableMessage).show()
            true
        }
    }

    private val editProfileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (DataStore.currentProfile == viewModel.editingID) {
                    needReload()
                }
                viewModel.editingID = null
            }
        }

    private val exportConfig =
        registerForActivityResult(CreateDocument("application/json")) { data ->
            if (data != null) {
                lifecycleScope.launch {
                    try {
                        requireActivity().contentResolver.openOutputStream(data)!!
                            .bufferedWriter().use {
                                it.write(viewModel.exportConfig)
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
}

/** Make server address blurred. */
private fun String.blur(): String = when (length) {
    in 0 until 20 -> {
        val halfLength = length / 2
        substring(0, halfLength) + "*".repeat(length - halfLength)
    }

    in 20..30 -> substring(0, 15) + "*".repeat(length - 15)

    else -> substring(0, 15) + "*".repeat(15)
}
