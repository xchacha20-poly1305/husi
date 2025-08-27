package io.nekohasekai.sagernet.ui.dashboard

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.databinding.LayoutDashboardBinding
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ToolbarFragment
import io.nekohasekai.sagernet.ktx.setOnFocusCancel
import kotlinx.coroutines.launch
import libcore.Libcore

class DashboardFragment : ToolbarFragment(R.layout.layout_dashboard),
    Toolbar.OnMenuItemClickListener,
    SearchView.OnQueryTextListener {

    companion object {
        const val POSITION_STATUS = 0
        const val POSITION_CONNECTIONS = 1
        const val POSITION_PROXY_SET = 2
    }

    private lateinit var binding: LayoutDashboardBinding
    private val viewModel by viewModels<DashboardFragmentViewModel>()
    private lateinit var adapter: TrafficAdapter

    private val menuTrafficSearch get() = toolbar.menu.findItem(R.id.action_traffic_search)!!
    private val menuTrafficPause get() = toolbar.menu.findItem(R.id.action_traffic_pause)!!

    private val menuSort get() = toolbar.menu.findItem(R.id.action_sort)!!
    private val menuSortMethod get() = toolbar.menu.findItem(R.id.action_sort_method)!!

    private val menuSortAscending get() = toolbar.menu.findItem(R.id.action_sort_ascending)!!
    private val menuSortDescending get() = toolbar.menu.findItem(R.id.action_sort_descending)!!

    private val menuSortTime get() = toolbar.menu.findItem(R.id.action_sort_time)!!
    private val menuSortInbound get() = toolbar.menu.findItem(R.id.action_sort_inbound)!!
    private val menuSortSource get() = toolbar.menu.findItem(R.id.action_sort_source)!!
    private val menuSortDestination get() = toolbar.menu.findItem(R.id.action_sort_destination)!!
    private val menuSortUpload get() = toolbar.menu.findItem(R.id.action_sort_upload)!!
    private val menuSortDownload get() = toolbar.menu.findItem(R.id.action_sort_download)!!
    private val menuSortRule get() = toolbar.menu.findItem(R.id.action_sort_rule)!!

    private val menuConnectionsStatusActive get() = toolbar.menu.findItem(R.id.action_connections_status_active)!!
    private val menuConnectionsStatusClosed get() = toolbar.menu.findItem(R.id.action_connections_status_closed)!!

    // private val menuResetNetwork get() = toolbar.menu.findItem(R.id.action_reset_network)!!

    private val searchView get() = menuTrafficSearch.actionView as SearchView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutDashboardBinding.bind(view)
        toolbar.setTitle(R.string.menu_dashboard)
        toolbar.inflateMenu(R.menu.dashboard_menu)
        toolbar.setOnMenuItemClickListener(this)

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

        searchView.apply {
            setOnQueryTextListener(this@DashboardFragment)
            maxWidth = Int.MAX_VALUE
            setOnFocusCancel()
            isVisible = true
        }

        fun updateMenu(isConnectionUI: Boolean) {
            menuTrafficSearch.isVisible = isConnectionUI
            menuTrafficPause.isVisible = isConnectionUI
            menuSort.isVisible = isConnectionUI
            menuSortMethod.isVisible = isConnectionUI
        }

        binding.dashboardTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.onTabSelected(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    if (it.isPausing) {
                        menuTrafficPause.setIcon(R.drawable.baseline_play_24)
                    } else {
                        menuTrafficPause.setIcon(R.drawable.baseline_pause_24)
                    }
                    updateMenu(it.isConnectionUiVisible)
                }
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.connectionState.collect(::handleConnectionState)
            }
        }

        viewModel.onTabSelected(binding.dashboardTab.selectedTabPosition)
    }

    private fun handleConnectionState(state: ConnectionState) {
        menuSortDescending.isChecked = state.isDescending
        menuSortAscending.isChecked = !state.isDescending

        menuSortTime.isChecked = state.sortMode == TrafficSortMode.START
        menuSortInbound.isChecked = state.sortMode == TrafficSortMode.INBOUND
        menuSortSource.isChecked = state.sortMode == TrafficSortMode.SRC
        menuSortDestination.isChecked = state.sortMode == TrafficSortMode.DST
        menuSortUpload.isChecked = state.sortMode == TrafficSortMode.UPLOAD
        menuSortDownload.isChecked = state.sortMode == TrafficSortMode.DOWNLOAD
        menuSortRule.isChecked = state.sortMode == TrafficSortMode.MATCHED_RULE

        val queryOptions = state.queryOptions
        menuConnectionsStatusActive.isChecked = queryOptions and Libcore.ShowTrackerActively != 0
        menuConnectionsStatusClosed.isChecked = queryOptions and Libcore.ShowTrackerClosed != 0
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset_network -> {
                val fragment = getFragment<ConnectionListFragment>(POSITION_CONNECTIONS) ?: return false
                val size = fragment.connectionSize()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.reset_connections)
                    .setMessage(getString(R.string.ensure_close_all, size.toString()))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            (requireActivity() as? MainActivity)?.connection?.service?.resetNetwork()
                        }
                        snackbar(R.string.have_reset_network).show()
                    }
                    .setNegativeButton(R.string.no_thanks, null)
                    .show()
                true
            }

            R.id.action_traffic_pause -> {
                viewModel.reversePausing()
                true
            }

            // Sort

            R.id.action_sort_ascending -> {
                item.isChecked = true
                viewModel.setSortDescending(false)
                true
            }

            R.id.action_sort_descending -> {
                item.isChecked = true
                viewModel.setSortDescending(true)
                true
            }

            R.id.action_sort_time -> {
                item.isChecked = true
                viewModel.setSortMode(TrafficSortMode.START)
                true
            }

            R.id.action_sort_inbound -> {
                item.isChecked = true
                viewModel.setSortMode(TrafficSortMode.INBOUND)
                true
            }

            R.id.action_sort_source -> {
                item.isChecked = true
                viewModel.setSortMode(TrafficSortMode.SRC)
                true
            }

            R.id.action_sort_destination -> {
                item.isChecked = true
                viewModel.setSortMode(TrafficSortMode.DST)
                true
            }

            R.id.action_sort_upload -> {
                item.isChecked = true
                viewModel.setSortMode(TrafficSortMode.UPLOAD)
                true
            }

            R.id.action_sort_download -> {
                item.isChecked = true
                viewModel.setSortMode(TrafficSortMode.DOWNLOAD)
                true
            }

            R.id.action_sort_rule -> {
                item.isChecked = true
                viewModel.setSortMode(TrafficSortMode.MATCHED_RULE)
                true
            }

            // Connection status

            R.id.action_connections_status_active -> {
                val queryActivate = !item.isChecked
                item.isChecked = queryActivate
                viewModel.queryActivate = queryActivate
                true
            }

            R.id.action_connections_status_closed -> {
                val queryClosed = !item.isChecked
                item.isChecked = queryClosed
                viewModel.queryClosed = queryClosed
                true
            }

            else -> false
        }
    }

    override fun onDestroy() {
        toolbar.setOnLongClickListener(null)
        super.onDestroy()
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

    override fun onQueryTextSubmit(query: String?): Boolean {
        viewModel.setSearchQuery(query)
        return false
    }

    override fun onQueryTextChange(query: String?): Boolean {
        viewModel.setSearchQuery(query)
        return false
    }
}
