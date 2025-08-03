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
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutDashboardBinding
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ToolbarFragment
import io.nekohasekai.sagernet.ktx.setOnFocusCancel
import kotlinx.coroutines.launch

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

    private val menuSearch by lazy { toolbar.menu.findItem(R.id.action_traffic_search) }
    private val menuPause by lazy { toolbar.menu.findItem(R.id.action_traffic_pause) }
    private val actionSort by lazy { toolbar.menu.findItem(R.id.action_sort) }
    private val actionSortMethod by lazy { toolbar.menu.findItem(R.id.action_sort_method) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutDashboardBinding.bind(view)
        toolbar.setTitle(R.string.menu_dashboard)
        toolbar.inflateMenu(R.menu.dashboard_menu)
        toolbar.setOnMenuItemClickListener(this)
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

        if (DataStore.trafficDescending) {
            toolbar.menu.findItem(R.id.action_sort_descending)!!.isChecked = true
        } else {
            toolbar.menu.findItem(R.id.action_sort_ascending)!!.isChecked = true
        }
        when (DataStore.trafficSortMode) {
            TrafficSortMode.START -> toolbar.menu.findItem(R.id.action_sort_time)!!.isChecked = true
            TrafficSortMode.INBOUND -> {
                toolbar.menu.findItem(R.id.action_sort_inbound)!!.isChecked = true
            }

            TrafficSortMode.SRC -> toolbar.menu.findItem(R.id.action_sort_source)!!.isChecked = true
            TrafficSortMode.DST -> {
                toolbar.menu.findItem(R.id.action_sort_destination)!!.isChecked = true
            }

            TrafficSortMode.UPLOAD -> {
                toolbar.menu.findItem(R.id.action_sort_upload)!!.isChecked = true
            }

            TrafficSortMode.DOWNLOAD -> {
                toolbar.menu.findItem(R.id.action_sort_download)!!.isChecked = true
            }

            TrafficSortMode.MATCHED_RULE -> {
                toolbar.menu.findItem(R.id.action_sort_rule)!!.isChecked = true
            }
        }
        toolbar.findViewById<SearchView>(R.id.action_traffic_search).apply {
            setOnQueryTextListener(this@DashboardFragment)
            maxWidth = Int.MAX_VALUE
            setOnFocusCancel()
            isVisible = true
        }

        fun updateMenu(isConnectionUI: Boolean) {
            menuSearch.isVisible = isConnectionUI
            menuPause.isVisible = isConnectionUI
            actionSort.isVisible = isConnectionUI
            actionSortMethod.isVisible = isConnectionUI
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
                        menuPause.setIcon(R.drawable.baseline_play_24)
                    } else {
                        menuPause.setIcon(R.drawable.baseline_pause_24)
                    }
                    updateMenu(it.isConnectionUiVisible)
                }
            }
        }
        viewModel.onTabSelected(binding.dashboardTab.selectedTabPosition)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset_network -> {
                val fragment =
                    getFragment<ConnectionListFragment>(POSITION_CONNECTIONS) ?: return false
                val size = fragment.connectionSize()
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.reset_connections)
                    .setMessage(
                        getString(
                            R.string.ensure_close_all,
                            size.toString(),
                        )
                    )
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
                DataStore.trafficDescending = true
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

            else -> false
        }
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
