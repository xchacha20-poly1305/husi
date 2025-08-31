package io.nekohasekai.sagernet.ui.configuration

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.adapter.FragmentViewHolder
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.databinding.LayoutGroupListBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressListBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.closeQuietly
import io.nekohasekai.sagernet.ktx.dp2pxf
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.getColour
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.readableUrlTestError
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.setOnFocusCancel
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ThemedActivity
import io.nekohasekai.sagernet.ui.ToolbarFragment
import io.nekohasekai.sagernet.ui.profile.AnyTLSSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ChainSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ConfigSettingActivity
import io.nekohasekai.sagernet.ui.profile.DirectSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HttpSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HysteriaSettingsActivity
import io.nekohasekai.sagernet.ui.profile.JuicitySettingsActivity
import io.nekohasekai.sagernet.ui.profile.MieruSettingsActivity
import io.nekohasekai.sagernet.ui.profile.NaiveSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ProxySetSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SSHSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowQUICSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowTLSSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowsocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrojanSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TuicSettingsActivity
import io.nekohasekai.sagernet.ui.profile.VMessSettingsActivity
import io.nekohasekai.sagernet.ui.profile.WireGuardSettingsActivity
import kotlinx.coroutines.launch
import java.util.zip.ZipInputStream

class ConfigurationFragment : ToolbarFragment,
    PopupMenu.OnMenuItemClickListener,
    Toolbar.OnMenuItemClickListener, SearchView.OnQueryTextListener {

    companion object {
        private const val PAYLOAD_NAME = 1 shl 0

        private const val ARG_FOR_SELECT = "for_select"
        private const val ARG_SELECTED_ITEM = "selected_item"
        private const val ARG_TITLE_RES = "title_res"
    }

    /**
     * @param forSelect marks this fragment starts for configuration selecting
     * but not display buttons.
     *
     * @param titleRes custom title ID.
     */
    @JvmOverloads
    constructor(
        forSelect: Boolean = false,
        selectedItem: ProxyEntity? = null,
        titleRes: Int = 0,
    ) : super(R.layout.layout_group_list) {
        arguments = bundleOf(
            ARG_FOR_SELECT to forSelect,
            ARG_SELECTED_ITEM to selectedItem,
            ARG_TITLE_RES to titleRes,
        )
    }

    interface SelectCallback {
        fun returnProfile(profileId: Long)
    }

    private lateinit var binding: LayoutGroupListBinding
    private lateinit var adapter: GroupPagerAdapter
    private val viewModel: ConfigurationFragmentViewModel by viewModels()

    private fun getCurrentGroupFragment(): GroupProfilesHolder? = try {
        childFragmentManager.findFragmentByTag("f" + DataStore.selectedGroup) as GroupProfilesHolder?
    } catch (e: Exception) {
        Logs.e(e)
        null
    }

    override fun onQueryTextChange(query: String?): Boolean {
        getCurrentGroupFragment()?.updateQuery(query)
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean = false

    @SuppressLint("DetachAndAttachSameFragment")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            viewModel.forSelect = it.getBoolean(ARG_FOR_SELECT)
            viewModel.selectedItem = BundleCompat.getParcelable(
                it,
                ARG_SELECTED_ITEM, ProxyEntity::class.java,
            )
            viewModel.titleRes = it.getInt(ARG_TITLE_RES)
        }
        if (savedInstanceState != null) {
            parentFragmentManager.beginTransaction()
                .setReorderingAllowed(false)
                .detach(this)
                .attach(this)
                .commit()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!viewModel.forSelect) {
            toolbar.inflateMenu(R.menu.add_profile_menu)
            toolbar.setOnMenuItemClickListener(this)
            toolbar.setTitleTextAppearance(context, R.style.AppNameAppearanceHusi)
        } else {
            toolbar.setTitle(viewModel.titleRes)
            toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
            toolbar.setNavigationOnClickListener {
                requireActivity().finish()
            }
        }
        val activity = requireActivity() as ThemedActivity
        val searchView = toolbar.findViewById<SearchView>(R.id.action_search)
        if (searchView != null) {
            searchView.setOnQueryTextListener(this)
            searchView.maxWidth = Int.MAX_VALUE
            searchView.setOnFocusCancel { hasFocus ->
                activity.onBackPressedCallback?.isEnabled = hasFocus
            }
        }

        binding = LayoutGroupListBinding.bind(view)

        ViewCompat.setOnApplyWindowInsetsListener(binding.groupTab) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
            )
            insets
        }

        binding.groupPager.adapter = GroupPagerAdapter().also {
            adapter = it
        }
        binding.groupPager.offscreenPageLimit = 2

        // Clean up search status when shifting tab.
        binding.groupTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {}

            override fun onTabUnselected(tab: TabLayout.Tab) {
                searchView?.setQuery(null, true)
            }

            override fun onTabReselected(tab: TabLayout.Tab) {}

        })

        TabLayoutMediator(binding.groupTab, binding.groupPager) { tab, position ->
            if (adapter.currentList.size > position) {
                tab.text = adapter.currentList[position].displayName()
            }
            tab.view.setOnLongClickListener { // clear toast
                true
            }
        }.attach()

        toolbar.setOnClickListener {
            getCurrentGroupFragment()?.scrollToProxy(
                viewModel.selectedItem?.id ?: DataStore.selectedProxy
            )
        }

        toolbar.setOnLongClickListener {
            val selectedProxy = viewModel.selectedItem
                ?: SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
                ?: return@setOnLongClickListener true
            val groupIndex = adapter.currentList.indexOfFirst {
                it.id == selectedProxy.groupId
            }
            if (groupIndex < 0) return@setOnLongClickListener true
            DataStore.selectedGroup = selectedProxy.groupId
            binding.groupPager.currentItem = groupIndex
            getCurrentGroupFragment()?.scrollToProxy(selectedProxy.id)
            true
        }

        activity.onBackPressedCallback?.isEnabled = false

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

    private fun handleUiState(state: ConfigurationFragmentUiState) {
        if (state.testState == null) {
            testDialog?.dismiss()
            testDialog = null
            testDialogBinding = null
        } else {
            if (testDialog == null) {
                testDialogBinding = LayoutProgressListBinding.inflate(layoutInflater)
                testDialogBinding!!.progressCircular.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
                    // Reset indeterminate mode to restore animation
                    // (fixes issue where animation stops after app switching / visiting recents tasks)
                    if (hasFocus) testDialogBinding?.progressCircular?.apply {
                        isIndeterminate = false
                        isIndeterminate = true
                    }
                }
                testDialog = MaterialAlertDialogBuilder(requireContext())
                    .setView(testDialogBinding!!.root)
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        viewModel.cancelTest()
                    }
                    .setOnDismissListener {
                        viewModel.cancelTest()
                        testDialog = null
                    }
                    .setCancelable(false)
                    .show()
            }

            val testState = state.testState
            testState.latestResult?.let { lastResult ->
                val (statusText, statusColor) = getStatusTextAndColor(lastResult.result)
                lastResult.profile.error = statusText

                val spannableText = SpannableStringBuilder().apply {
                    append("\n" + lastResult.profile.displayName())
                    append("\n")
                    append(
                        lastResult.profile.displayType(),
                        ForegroundColorSpan(requireContext().getColorAttr(androidx.appcompat.R.attr.colorAccent)),
                        SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    append(" ")
                    append(
                        statusText,
                        ForegroundColorSpan(statusColor),
                        SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    append("\n")
                }
                testDialogBinding!!.nowTesting.text = spannableText
            }
            testDialogBinding!!.progress.text = "${testState.processedCount} / ${testState.total}"
        }

        adapter.submitList(state.groups)
        val hideTab = state.groups.size < 2
        binding.groupTab.isGone = hideTab
        toolbar.elevation = if (hideTab) {
            0F
        } else {
            dp2pxf(4)
        }

        if (binding.groupPager.currentItem != state.selectedGroupIndex && state.groups.isNotEmpty()) {
            binding.groupPager.setCurrentItem(state.selectedGroupIndex, false)
        }
    }

    private fun handleUiEvent(event: ConfigurationFragmentUiEvent) {
        Logs.w("handle")
        when (event) {
            is ConfigurationFragmentUiEvent.ProfileSelect -> {
                Logs.w("event")
                for ((_, holder) in adapter.fragments) {
                    Logs.w(holder.javaClass.name)
                    holder.newSelect(event.new)
                }
            }
        }
    }

    override fun onKeyDown(ketCode: Int, event: KeyEvent): Boolean {
        getCurrentGroupFragment()?.requestFocusIfNotHave()
        return super.onKeyDown(ketCode, event)
    }

    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
            if (file != null) runOnDefaultDispatcher {
                try {
                    val fileName =
                        requireContext().contentResolver.query(file, null, null, null, null)
                            ?.use { cursor ->
                                cursor.moveToFirst()
                                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                                    .let(cursor::getString)
                            }
                    val proxies = mutableListOf<AbstractBean>()
                    if (fileName != null && fileName.endsWith(".zip")) {
                        // try parse wireguard zip
                        val zip =
                            ZipInputStream(requireContext().contentResolver.openInputStream(file)!!)
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.isDirectory) continue
                            val fileText = zip.bufferedReader().readText()
                            RawUpdater.parseRaw(fileText, entry.name)
                                ?.let { pl -> proxies.addAll(pl) }
                            zip.closeEntry()
                        }
                        zip.closeQuietly()
                    } else {
                        val fileText =
                            requireContext().contentResolver.openInputStream(file)!!.use {
                                it.bufferedReader().readText()
                            }
                        RawUpdater.parseRaw(fileText, fileName ?: "")
                            ?.let { pl -> proxies.addAll(pl) }
                    }
                    if (proxies.isEmpty()) onMainDispatcher {
                        snackbar(getString(R.string.no_proxies_found_in_file)).show()
                    } else {
                        (requireActivity() as MainActivity).importProfile(proxies)
                    }
                } catch (e: SubscriptionFoundException) {
                    (requireActivity() as MainActivity).importSubscription(e.link.toUri())
                } catch (e: Exception) {
                    Logs.w(e)
                    onMainDispatcher {
                        snackbar(e.readableMessage).show()
                    }
                }
            }
        }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_scan_qr_code -> {
                startActivity(Intent(context, ScannerActivity::class.java))
            }

            R.id.action_import_clipboard -> {
                (requireActivity() as MainActivity).parseProxy(SagerNet.getClipboardText())
            }

            R.id.action_import_file -> {
                startFilesForResult(importFile, "*/*")
            }

            R.id.action_new_socks -> {
                startActivity(Intent(requireActivity(), SocksSettingsActivity::class.java))
            }

            R.id.action_new_http -> {
                startActivity(Intent(requireActivity(), HttpSettingsActivity::class.java))
            }

            R.id.action_new_ss -> {
                startActivity(Intent(requireActivity(), ShadowsocksSettingsActivity::class.java))
            }

            R.id.action_new_vmess -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java))
            }

            R.id.action_new_vless -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java).apply {
                    putExtra(VMessSettingsActivity.EXTRA_VLESS, true)
                })
            }

            R.id.action_new_trojan -> {
                startActivity(Intent(requireActivity(), TrojanSettingsActivity::class.java))
            }

            R.id.action_new_mieru -> {
                startActivity(Intent(requireActivity(), MieruSettingsActivity::class.java))
            }

            R.id.action_new_naive -> {
                startActivity(Intent(requireActivity(), NaiveSettingsActivity::class.java))
            }

            R.id.action_new_hysteria -> {
                startActivity(Intent(requireActivity(), HysteriaSettingsActivity::class.java))
            }

            R.id.action_new_tuic -> {
                startActivity(Intent(requireActivity(), TuicSettingsActivity::class.java))
            }

            R.id.action_new_juicity -> {
                startActivity(Intent(requireActivity(), JuicitySettingsActivity::class.java))
            }

            R.id.action_new_direct -> {
                startActivity(Intent(requireActivity(), DirectSettingsActivity::class.java))
            }

            R.id.action_new_ssh -> {
                startActivity(Intent(requireActivity(), SSHSettingsActivity::class.java))
            }

            R.id.action_new_wg -> {
                startActivity(Intent(requireActivity(), WireGuardSettingsActivity::class.java))
            }

            R.id.action_new_shadowtls -> {
                startActivity(Intent(requireActivity(), ShadowTLSSettingsActivity::class.java))
            }

            R.id.action_new_anytls -> {
                startActivity(Intent(requireActivity(), AnyTLSSettingsActivity::class.java))
            }

            R.id.action_new_shadowquic -> {
                startActivity(Intent(requireActivity(), ShadowQUICSettingsActivity::class.java))
            }

            R.id.action_new_proxy_set -> {
                startActivity(Intent(requireActivity(), ProxySetSettingsActivity::class.java))
            }

            R.id.action_new_config -> {
                startActivity(Intent(requireActivity(), ConfigSettingActivity::class.java))
            }

            R.id.action_new_chain -> {
                startActivity(Intent(requireActivity(), ChainSettingsActivity::class.java))
            }

            R.id.action_clear_traffic_statistics -> {
                getCurrentGroupFragment()?.clearTrafficStatistics()
            }

            R.id.action_connection_test_clear_results -> {
                getCurrentGroupFragment()?.clearResults()
            }

            R.id.action_connection_test_delete_unavailable -> {
                getCurrentGroupFragment()?.deleteUnavailable()
            }

            R.id.action_remove_duplicate -> {
                getCurrentGroupFragment()?.removeDuplicate()
            }

            R.id.action_connection_icmp_ping -> {
                viewModel.doTest(DataStore.currentGroupId(), TestType.ICMPPing)
            }

            R.id.action_connection_tcp_ping -> {
                viewModel.doTest(DataStore.currentGroupId(), TestType.TCPPing)
            }

            R.id.action_connection_url_test -> {
                viewModel.doTest(DataStore.currentGroupId(), TestType.URLTest)
            }
        }
        return true
    }

    private var testDialog: AlertDialog? = null
    private var testDialogBinding: LayoutProgressListBinding? = null

    private fun getStatusTextAndColor(result: TestResult): Pair<String, Int> {
        val context = requireContext()
        when (result) {
            is TestResult.Success -> {
                return getString(
                    R.string.available,
                    result.ping
                ) to context.getColour(R.color.material_green_500)
            }

            is TestResult.Failure -> {
                val color = context.getColour(R.color.material_red_500)
                val text = when (val reason = result.reason) {
                    FailureReason.InvalidConfig -> getString(R.string.unavailable)
                    FailureReason.DomainNotFound -> getString(R.string.connection_test_domain_not_found)
                    FailureReason.IcmpUnavailable -> getString(R.string.connection_test_icmp_ping_unavailable)
                    FailureReason.TcpUnavailable -> getString(R.string.connection_test_tcp_ping_unavailable)
                    FailureReason.ConnectionRefused -> getString(R.string.connection_test_refused)
                    FailureReason.NetworkUnreachable -> getString(R.string.connection_test_unreachable)
                    FailureReason.Timeout -> getString(R.string.connection_test_timeout)

                    is FailureReason.Generic -> reason.message?.let { error ->
                        readableUrlTestError(error)?.let {
                            getString(it)
                        }
                    } ?: getString(R.string.unavailable)

                    is FailureReason.PluginNotFound -> reason.message
                }
                return text to color
            }
        }
    }

    private inner class GroupPagerAdapter : FragmentStateAdapter(this) {

        val currentList = mutableListOf<ProxyGroup>()
        val fragments = mutableMapOf<Long, GroupProfilesHolder>()

        fun submitList(groups: List<ProxyGroup>) {
            val diffCallback = object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = currentList.size

                override fun getNewListSize(): Int = groups.size

                override fun areItemsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    return currentList[oldItemPosition].id == groups[newItemPosition].id
                }

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    val old = currentList[oldItemPosition]
                    val new = groups[newItemPosition]
                    return old.name == new.name
                }

                override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                    val old = currentList[oldItemPosition]
                    val new = groups[newItemPosition]
                    var mask = 0
                    if (old.name != new.name) {
                        mask = mask or PAYLOAD_NAME
                    }
                    return if (mask == 0) {
                        super.getChangePayload(oldItemPosition, newItemPosition)
                    } else {
                        mask
                    }
                }
            }
            val result = DiffUtil.calculateDiff(diffCallback)
            if (!viewModel.forSelect) {
                binding.groupPager.unregisterOnPageChangeCallback(updateSelectedCallback)
            }
            currentList.clear()
            currentList.addAll(groups)
            result.dispatchUpdatesTo(this)
            if (!viewModel.forSelect) {
                binding.groupPager.registerOnPageChangeCallback(updateSelectedCallback)
            }
        }

        override fun getItemCount(): Int {
            return currentList.size
        }

        override fun createFragment(position: Int): Fragment {
            val group = currentList[position]
            return GroupProfilesHolder(
                viewModel.forSelect,
                group,
                viewModel.selectedItem?.id,
            ).also {
                fragments[group.id] = it
            }
        }

        override fun onBindViewHolder(
            holder: FragmentViewHolder,
            position: Int,
            payloads: List<Any?>
        ) {
            var mask = 0
            for (payload in payloads) {
                mask = mask or payload as Int
            }
            if (mask == 0) {
                super.onBindViewHolder(holder, position, payloads)
            } else {
                val group = currentList[position]
                if (mask and PAYLOAD_NAME != 0) {
                    binding.groupTab.getTabAt(position)?.text = group.displayName()
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return currentList[position].id
        }

        override fun containsItem(itemId: Long): Boolean {
            return currentList.any { it.id == itemId }
        }

        private val updateSelectedCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (adapter.currentList.size > position) {
                    viewModel.onSelect(adapter.currentList[position].id)
                }
            }
        }
    }

}
