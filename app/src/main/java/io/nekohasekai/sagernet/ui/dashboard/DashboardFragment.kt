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
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private lateinit var adapter: TrafficAdapter

    private val menuSearch by lazy { toolbar.menu.findItem(R.id.action_traffic_search) }
    private val menuPause by lazy { toolbar.menu.findItem(R.id.action_traffic_pause) }
    private val actionSort by lazy { toolbar.menu.findItem(R.id.action_sort) }
    private val actionSortMethod by lazy { toolbar.menu.findItem(R.id.action_sort_method) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutDashboardBinding.bind(view)
        toolbar.setTitle(R.string.menu_dashboard)
        toolbar.inflateMenu(R.menu.traffic_menu)
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
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.dashboardTab) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
            )
            WindowInsetsCompat.CONSUMED
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
            WindowInsetsCompat.CONSUMED
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
        searchView = toolbar.findViewById<SearchView>(R.id.action_traffic_search).apply {
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
                val isConnectionUI = tab.position == POSITION_CONNECTIONS
                updateMenu(isConnectionUI)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}

        })
        updateMenu(false)

        lifecycleScope.launch {
            val interval = DataStore.speedInterval.takeIf { it > 0 }?.toLong() ?: 1000L
            while (isActive) {
                emitStats()
                delay(interval)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset_network -> {
                val mainActivity = (requireActivity() as? MainActivity) ?: return true
                val size =
                    (adapter.getCurrentFragment(POSITION_CONNECTIONS) as? ConnectionListFragment)
                        ?.adapter?.data?.size ?: 0
                MaterialAlertDialogBuilder(mainActivity)
                    .setTitle(R.string.reset_connections)
                    .setMessage(
                        getString(
                            R.string.ensure_close_all,
                            size.toString(),
                        )
                    )
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        mainActivity.connection.service?.resetNetwork()
                        snackbar(R.string.have_reset_network).show()
                    }
                    .setNegativeButton(R.string.no_thanks) { _, _ -> }
                    .show()
                true
            }

            R.id.action_traffic_pause -> {
                if (isPausing) {
                    isPausing = false
                    item.setIcon(android.R.drawable.ic_media_pause)
                } else {
                    isPausing = true
                    item.setIcon(android.R.drawable.ic_media_play)
                }
                true
            }

            // Sort

            R.id.action_sort_ascending -> {
                DataStore.trafficDescending = false
                item.isChecked = true
                true
            }

            R.id.action_sort_descending -> {
                DataStore.trafficDescending = true
                item.isChecked = true
                true
            }

            R.id.action_sort_time -> {
                DataStore.trafficSortMode = TrafficSortMode.START
                item.isChecked = true
                true
            }

            R.id.action_sort_inbound -> {
                DataStore.trafficSortMode = TrafficSortMode.INBOUND
                item.isChecked = true
                true
            }

            R.id.action_sort_source -> {
                DataStore.trafficSortMode = TrafficSortMode.SRC
                item.isChecked = true
                true
            }

            R.id.action_sort_destination -> {
                DataStore.trafficSortMode = TrafficSortMode.DST
                item.isChecked = true
                true
            }

            R.id.action_sort_upload -> {
                DataStore.trafficSortMode = TrafficSortMode.UPLOAD
                item.isChecked = true
                true
            }

            R.id.action_sort_download -> {
                DataStore.trafficSortMode = TrafficSortMode.DOWNLOAD
                item.isChecked = true
                true
            }

            R.id.action_sort_rule -> {
                DataStore.trafficSortMode = TrafficSortMode.MATCHED_RULE
                item.isChecked = true
                true
            }

            else -> false
        }
    }

    private var isPausing = false

    private suspend fun emitStats() {
        when (binding.dashboardPager.currentItem) {
            POSITION_STATUS -> {
                val dashboard = getFragment(POSITION_STATUS) as? StatusFragment ?: return
                val service = (requireActivity() as? MainActivity)?.connection?.service ?: return
                dashboard.emitStats(service.queryMemory(), service.queryGoroutines())
            }

            POSITION_CONNECTIONS -> {
                if (isPausing) return
                val connectionFragment =
                    getFragment(POSITION_CONNECTIONS) as? ConnectionListFragment ?: return
                val service = (requireActivity() as? MainActivity)?.connection?.service ?: return
                connectionFragment.emitStats(service.queryConnections().connections)
            }

            // TODO proxy set use emitStats, too.
            POSITION_PROXY_SET -> {}
        }
    }

    fun refreshClashMode() {
        if (binding.dashboardPager.currentItem != POSITION_STATUS) return
        (getFragment(POSITION_STATUS) as? StatusFragment)?.refreshClashMode()
    }

    private fun getFragment(position: Int): Fragment? {
        return adapter.getCurrentFragment(position)
    }

    inner class TrafficAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
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

        fun getCurrentFragment(position: Int): Fragment? {
            return childFragmentManager.findFragmentByTag("f$position")
        }
    }

    private lateinit var searchView: SearchView

    override fun onQueryTextSubmit(query: String?): Boolean {
        (getFragment(POSITION_CONNECTIONS) as? ConnectionListFragment)?.searchString = query
        return false
    }

    override fun onQueryTextChange(query: String?): Boolean {
        (getFragment(POSITION_CONNECTIONS) as? ConnectionListFragment)?.searchString = query
        return false
    }
}