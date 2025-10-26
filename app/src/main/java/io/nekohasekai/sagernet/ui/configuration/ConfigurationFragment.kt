package io.nekohasekai.sagernet.ui.configuration

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.nekohasekai.sagernet.compose.ExpandableDropdownMenuItem
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.displayType
import io.nekohasekai.sagernet.databinding.LayoutGroupListBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressListBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.closeQuietly
import io.nekohasekai.sagernet.ktx.dp2pxf
import io.nekohasekai.sagernet.ktx.first
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.getColour
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.readableUrlTestError
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.OnKeyDownFragment
import io.nekohasekai.sagernet.ui.ThemedActivity
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
import io.nekohasekai.sagernet.ui.profile.VLESSSettingsActivity
import io.nekohasekai.sagernet.ui.profile.VMessSettingsActivity
import io.nekohasekai.sagernet.ui.profile.WireGuardSettingsActivity
import kotlinx.coroutines.launch
import java.util.zip.ZipInputStream

@OptIn(ExperimentalMaterial3Api::class)
class ConfigurationFragment : OnKeyDownFragment {

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

    private val clipboard by lazy { requireContext().getSystemService<ClipboardManager>()!! }

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
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as ThemedActivity
        binding = LayoutGroupListBinding.bind(view)
        composeToolbar()

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
                viewModel.setSearchQuery("")
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

        activity.onBackPressedCallback?.isEnabled = false

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
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
                        lastResult.profile.displayType(requireContext()),
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
        binding.toolbar.elevation = if (hideTab) {
            0F
        } else {
            dp2pxf(4)
        }

        if (binding.groupPager.currentItem != state.selectedGroupIndex && state.groups.isNotEmpty()) {
            binding.groupPager.setCurrentItem(state.selectedGroupIndex, false)
        }
    }

    override fun onKeyDown(ketCode: Int, event: KeyEvent): Boolean {
        lifecycleScope.launch {
            viewModel.emitChildEvent(ConfigurationChildEvent.RequestFocusIfNotHave(DataStore.selectedGroup))
        }
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

    fun composeToolbar(order: Int = 0) {
        binding.toolbar.setContent {
            @Suppress("DEPRECATION")
            AppTheme {
                MyTopAppBar(order)
            }
        }
    }

    @Composable
    private fun MyTopAppBar(initialOrder: Int) {
        // 说文小篆（虎兕）
        // Copyright: https://www.zdic.net/aboutus/copyright/ (A copy of CC0 1.0 was embed in the font file)
        val appNameFont by lazy { FontFamily(Font(R.font.shuowenxiaozhuan_husi)) }
        val isChinese = Locale.current.language == "zh"

        var showAddMenu by remember { mutableStateOf(false) }
        var showAddManualMenu by remember { mutableStateOf(false) }
        var showOverflowMenu by remember { mutableStateOf(false) }
        var showConnectionTestMenu by remember { mutableStateOf(false) }
        var showOrderMenu by remember { mutableStateOf(false) }
        var isSearchActive by remember { mutableStateOf(false) }
        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
        val focusManager = LocalFocusManager.current
        var order by remember { mutableIntStateOf(initialOrder) }

        TopAppBar(
            title = {
                if (isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text(stringResource(android.R.string.search_go)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            viewModel.setSearchQuery(searchQuery)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        )
                    )
                } else {
                    Text(
                        text = stringResource(
                            if (viewModel.forSelect) {
                                viewModel.titleRes
                            } else {
                                R.string.app_name
                            }
                        ),
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                lifecycleScope.launch {
                                    viewModel.emitChildEvent(
                                        ConfigurationChildEvent.ScrollToProxy(
                                            DataStore.selectedGroup,
                                            viewModel.selectedItem?.id ?: DataStore.selectedProxy,
                                            true,
                                        )
                                    )
                                }
                            },
                            onLongClick = {
                                lifecycleScope.launch {
                                    val selectedProxy = viewModel.selectedItem
                                        ?: SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
                                        ?: return@launch
                                    val groupIndex = adapter.currentList.indexOfFirst {
                                        it.id == selectedProxy.groupId
                                    }
                                    if (groupIndex < 0) return@launch
                                    DataStore.selectedGroup = selectedProxy.groupId
                                    binding.groupPager.currentItem = groupIndex
                                    viewModel.emitChildEvent(
                                        ConfigurationChildEvent.ScrollToProxy(
                                            selectedProxy.groupId,
                                            selectedProxy.id,
                                        )
                                    )
                                }
                            },
                        ),
                        style = if (isChinese) {
                            MaterialTheme.typography.titleLarge.copy(fontFamily = appNameFont)
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                    )
                }
            },
            navigationIcon = {
                if (viewModel.forSelect) SimpleIconButton(
                    imageVector = ImageVector.vectorResource(R.drawable.close),
                    contentDescription = stringResource(R.string.close),
                ) {
                    requireActivity().finish()
                } else SimpleIconButton(
                    imageVector = ImageVector.vectorResource(R.drawable.menu),
                    contentDescription = stringResource(R.string.menu),
                ) {
                    (requireActivity() as MainActivity).binding
                        .drawerLayout.openDrawer(GravityCompat.START)
                }
            },
            actions = {
                if (viewModel.forSelect) return@TopAppBar
                if (isSearchActive) SimpleIconButton(
                    imageVector = ImageVector.vectorResource(R.drawable.close),
                    contentDescription = stringResource(R.string.close),
                ) {
                    isSearchActive = false
                    viewModel.setSearchQuery("")
                } else {
                    SimpleIconButton(
                        imageVector = ImageVector.vectorResource(R.drawable.search),
                        contentDescription = stringResource(android.R.string.search_go),
                    ) {
                        isSearchActive = true
                    }

                    Box {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.note_add),
                            contentDescription = stringResource(R.string.add_profile),
                        ) {
                            showAddMenu = true
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_profile_methods_scan_qr_code)) },
                                onClick = {
                                    startActivity(Intent(context, ScannerActivity::class.java))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_import)) },
                                onClick = {
                                    showAddMenu = false
                                    runOnDefaultDispatcher {
                                        (requireActivity() as MainActivity).parseProxy(clipboard.first() ?: "")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_import_file)) },
                                onClick = {
                                    showAddMenu = false
                                    startFilesForResult(importFile, "*/*")
                                }
                            )
                            ExpandableDropdownMenuItem(stringResource(R.string.add_profile_methods_manual_settings)) {
                                showAddMenu = false
                                showAddManualMenu = true
                            }
                        }
                        DropdownMenu(
                            expanded = showAddManualMenu,
                            onDismissRequest = { showAddManualMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_socks)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            SocksSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_http)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            HttpSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_shadowsocks)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            ShadowsocksSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_vmess)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            VMessSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_vless)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            VLESSSettingsActivity::class.java,
                                        ),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_trojan)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            TrojanSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_mieru)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            MieruSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_naive)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            NaiveSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_hysteria)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            HysteriaSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_tuic)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            TuicSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_juicity)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            JuicitySettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_direct)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            DirectSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_ssh)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            SSHSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_wireguard)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            WireGuardSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_shadowtls)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            ShadowTLSSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_anytls)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            AnyTLSSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_shadowquic)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            ShadowQUICSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.proxy_set)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            ProxySetSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.custom_config)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            ConfigSettingActivity::class.java,
                                        )
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.proxy_chain)) },
                                onClick = {
                                    showAddManualMenu = false
                                    startActivity(
                                        Intent(
                                            requireContext(),
                                            ChainSettingsActivity::class.java,
                                        )
                                    )
                                },
                            )
                        }
                    }

                    Box {
                        SimpleIconButton(
                            imageVector = ImageVector.vectorResource(R.drawable.more_vert),
                            contentDescription = stringResource(R.string.more),
                        ) {
                            showOverflowMenu = true
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_traffic_statistics)) },
                                onClick = {
                                    showOverflowMenu = false
                                    lifecycleScope.launch {
                                        viewModel.emitChildEvent(
                                            ConfigurationChildEvent.ClearTrafficStatistic(
                                                DataStore.selectedGroup
                                            )
                                        )
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove_duplicate)) },
                                onClick = {
                                    showOverflowMenu = false
                                    lifecycleScope.launch {
                                        viewModel.emitChildEvent(
                                            ConfigurationChildEvent.RemoveDuplicate(
                                                DataStore.selectedGroup,
                                            )
                                        )
                                    }
                                },
                            )
                            ExpandableDropdownMenuItem(stringResource(R.string.connection_test)) {
                                showOverflowMenu = false
                                showConnectionTestMenu = true
                            }
                            ExpandableDropdownMenuItem(stringResource(R.string.sort_mode)) {
                                showOverflowMenu = false
                                showOrderMenu = true
                            }
                        }
                        DropdownMenu(
                            expanded = showConnectionTestMenu,
                            onDismissRequest = { showConnectionTestMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.connection_test_icmp_ping)) },
                                onClick = {
                                    showConnectionTestMenu = false
                                    viewModel.doTest(DataStore.currentGroupId(), TestType.ICMPPing)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.connection_test_tcp_ping)) },
                                onClick = {
                                    showConnectionTestMenu = false
                                    viewModel.doTest(DataStore.currentGroupId(), TestType.TCPPing)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.connection_test_url_test)) },
                                onClick = {
                                    showConnectionTestMenu = false
                                    viewModel.doTest(DataStore.currentGroupId(), TestType.URLTest)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.connection_test_delete_unavailable)) },
                                onClick = {
                                    showOverflowMenu = false
                                    lifecycleScope.launch {
                                        viewModel.emitChildEvent(
                                            ConfigurationChildEvent.DeleteUnavailable(
                                                DataStore.selectedGroup
                                            )
                                        )
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.connection_test_clear_results)) },
                                onClick = {
                                    showOverflowMenu = false
                                    lifecycleScope.launch {
                                        viewModel.emitChildEvent(
                                            ConfigurationChildEvent.ClearResult(
                                                DataStore.selectedGroup
                                            )
                                        )
                                    }
                                },
                            )
                        }
                        DropdownMenu(
                            expanded = showOrderMenu,
                            onDismissRequest = { showOrderMenu = false },
                        ) {
                            val orders = listOf(
                                stringResource(R.string.group_order_origin),
                                stringResource(R.string.group_order_by_name),
                                stringResource(R.string.group_order_by_delay),
                            )
                            orders.forEachIndexed { i, option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(
                                                selected = (order == i),
                                                onClick = null
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(text = option)
                                        }
                                    },
                                    onClick = {
                                        order = i
                                        showOrderMenu = false
                                        lifecycleScope.launch {
                                            viewModel.emitChildEvent(
                                                ConfigurationChildEvent.UpdateOrder(
                                                    DataStore.selectedGroup,
                                                    i,
                                                )
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
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
                    newItemPosition: Int,
                ): Boolean {
                    return currentList[oldItemPosition].id == groups[newItemPosition].id
                }

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int,
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
            payloads: List<Any?>,
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
