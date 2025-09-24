package io.nekohasekai.sagernet.ui.dashboard

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.compose.SimpleIconButton
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.databinding.LayoutDashboardBinding
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.OnKeyDownFragment
import kotlinx.coroutines.launch

class DashboardFragment : OnKeyDownFragment(R.layout.layout_dashboard) {

    companion object {
        const val POSITION_STATUS = 0
        const val POSITION_CONNECTIONS = 1
        const val POSITION_PROXY_SET = 2
    }

    private lateinit var binding: LayoutDashboardBinding
    private val viewModel by viewModels<DashboardFragmentViewModel>()
    private lateinit var adapter: TrafficAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutDashboardBinding.bind(view)
        binding.toolbar.setContent {
            @Suppress("DEPRECATION")
            AppTheme {
                var isSearchActive by remember { mutableStateOf(false) }
                val toolbarState by viewModel.toolbarState.collectAsStateWithLifecycle()
                val focusManager = LocalFocusManager.current
                var isOverflowMenuExpanded by remember { mutableStateOf(false) }
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            OutlinedTextField(
                                value = toolbarState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text(stringResource(android.R.string.search_go)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSend = {
                                    focusManager.clearFocus()
                                }),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    unfocusedIndicatorColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    unfocusedContainerColor =
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    focusedContainerColor =
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                )
                            )
                        } else {
                            Text(stringResource(R.string.menu_dashboard))
                        }
                    },
                    navigationIcon = {
                        SimpleIconButton(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.menu),
                        ) {
                            (requireActivity() as MainActivity).binding
                                .drawerLayout.openDrawer(GravityCompat.START)
                        }
                    },
                    actions = {
                        if (isSearchActive) SimpleIconButton(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                        ) {
                            isSearchActive = false
                            viewModel.setSearchQuery("")
                        } else {
                            SimpleIconButton(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(android.R.string.search_go),
                            ) {
                                isSearchActive = true
                            }
                            SimpleIconButton(
                                imageVector = if (toolbarState.isPause) {
                                    Icons.Filled.PlayArrow
                                } else {
                                    Icons.Filled.Pause
                                },
                                contentDescription = stringResource(R.string.pause),
                            ) {
                                viewModel.togglePausing()
                            }
                            SimpleIconButton(
                                imageVector = Icons.Filled.CleaningServices,
                                contentDescription = stringResource(R.string.reset_connections),
                            ) {
                                val size = getFragment<ConnectionListFragment>(POSITION_CONNECTIONS)
                                    ?.connectionSize() ?: 0
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.reset_connections)
                                    .setMessage(
                                        getString(R.string.ensure_close_all, size.toString())
                                    )
                                    .setPositiveButton(android.R.string.ok) { _, _ ->
                                        lifecycleScope.launch {
                                            (requireActivity() as? MainActivity)?.connection?.service?.resetNetwork()
                                        }
                                        snackbar(R.string.have_reset_network).show()
                                    }
                                    .setNegativeButton(R.string.no_thanks, null)
                                    .show()
                            }

                            Box {
                                SimpleIconButton(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = null,
                                ) {
                                    isOverflowMenuExpanded = true
                                }

                                DropdownMenu(
                                    expanded = isOverflowMenuExpanded,
                                    onDismissRequest = { isOverflowMenuExpanded = false }
                                ) {
                                    Text(
                                        text = stringResource(R.string.sort),
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp,
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ascending)) },
                                        onClick = {
                                            viewModel.setSortDescending(false)
                                            isOverflowMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = !toolbarState.isDescending,
                                                onClick = null,
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.descending)) },
                                        onClick = {
                                            viewModel.setSortDescending(true)
                                            isOverflowMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = toolbarState.isDescending,
                                                onClick = null,
                                            )
                                        }
                                    )

                                    HorizontalDivider()

                                    Text(
                                        stringResource(R.string.sort_mode),
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp,
                                        ),
                                        style = MaterialTheme.typography.titleSmall
                                    )

                                    for (sortMode in TrafficSortMode.values) {
                                        SortItem(
                                            isSelected = toolbarState.sortMode == sortMode,
                                            mode = sortMode,
                                        ) {
                                            viewModel.setSortMode(sortMode)
                                        }
                                    }

                                    HorizontalDivider()

                                    Text(
                                        stringResource(R.string.connection_status),
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp,
                                        ),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.connection_status_active)) },
                                        onClick = {
                                            viewModel.queryActivate = !toolbarState.showActivate
                                        },
                                        leadingIcon = {
                                            Checkbox(
                                                checked = toolbarState.showActivate,
                                                onCheckedChange = null,
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.connection_status_closed)) },
                                        onClick = {
                                            viewModel.queryClosed = !toolbarState.showClosed
                                        },
                                        leadingIcon = {
                                            Checkbox(
                                                checked = toolbarState.showClosed,
                                                onCheckedChange = null,
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.dashboardTab) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.dashboardPager) { v, insets ->
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

        binding.dashboardPager.adapter = TrafficAdapter(this).also {
            adapter = it
        }
        TabLayoutMediator(binding.dashboardTab, binding.dashboardPager) { tab, position ->
            tab.text = when (position) {
                POSITION_STATUS -> getString(R.string.traffic_status)
                POSITION_CONNECTIONS -> getString(R.string.traffic_connections)
                POSITION_PROXY_SET -> getString(R.string.proxy_set)
                else -> throw IllegalArgumentException()
            }
            tab.view.setOnLongClickListener {
                // clear toast
                true
            }
        }.attach()
    }

    override fun onDestroyView() {
        viewModel.setSearchQuery("")
        super.onDestroyView()
    }

    @Composable
    private fun SortItem(isSelected: Boolean, mode: Int, onClick: () -> Unit) {
        val text = when (mode) {
            TrafficSortMode.START -> R.string.by_time
            TrafficSortMode.INBOUND -> R.string.by_inbound
            TrafficSortMode.UPLOAD -> R.string.by_upload
            TrafficSortMode.DOWNLOAD -> R.string.by_download
            TrafficSortMode.SRC -> R.string.by_source
            TrafficSortMode.DST -> R.string.by_destination
            TrafficSortMode.MATCHED_RULE -> R.string.by_matched_rule
            else -> throw IllegalArgumentException("$mode impossible")
        }
        DropdownMenuItem(
            text = { Text(stringResource(text)) },
            onClick = onClick,
            leadingIcon = {
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                )
            }
        )
    }

    private inline fun <reified T : Fragment> getFragment(position: Int): T? {
        return childFragmentManager.findFragmentByTag("f$position") as? T
    }

    class TrafficAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int {
            return 3
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                POSITION_STATUS -> StatusFragment()
                POSITION_CONNECTIONS -> ConnectionListFragment()
                POSITION_PROXY_SET -> ProxySetFragment()
                else -> throw IllegalArgumentException()
            }
        }
    }
}
