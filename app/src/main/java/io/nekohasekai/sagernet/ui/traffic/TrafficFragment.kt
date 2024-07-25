package io.nekohasekai.sagernet.ui.traffic

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.TrafficSortMode
import io.nekohasekai.sagernet.aidl.DashboardStatus
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutTrafficBinding
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ToolbarFragment
import moe.matsuri.nb4a.utils.setOnFocusCancel

const val POSITION_DASH = 0
const val POSITION_CONNECTIONS = 1

class TrafficFragment : ToolbarFragment(R.layout.layout_traffic),
    Toolbar.OnMenuItemClickListener,
    SearchView.OnQueryTextListener {

    private lateinit var binding: LayoutTrafficBinding
    private lateinit var adapter: TrafficAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = LayoutTrafficBinding.bind(view)
        toolbar.setTitle(R.string.menu_dashboard)
        toolbar.inflateMenu(R.menu.traffic_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding.trafficPager.adapter = TrafficAdapter(this).also {
            adapter = it
        }
        TabLayoutMediator(binding.trafficTab, binding.trafficPager) { tab, position ->
            tab.text = when (position) {
                POSITION_DASH -> getString(R.string.traffic_status)
                POSITION_CONNECTIONS -> getString(R.string.traffic_connections)
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
        searchView = toolbar.findViewById<SearchView?>(R.id.action_traffic_search).apply {
            setOnQueryTextListener(this@TrafficFragment)
            maxWidth = Int.MAX_VALUE
            setOnFocusCancel()
            isVisible = true
        }
        (requireActivity() as MainActivity).connection.service?.enableDashboardStatus(true)
    }

    override fun onDestroyView() {
        (requireActivity() as MainActivity).connection.service?.enableDashboardStatus(false)
        super.onDestroyView()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_reset_network -> (requireActivity() as? MainActivity)?.let {
                val size =
                    (adapter.getCurrentFragment(POSITION_CONNECTIONS) as? ConnectionListFragment)
                        ?.adapter?.data?.size ?: 0
                MaterialAlertDialogBuilder(it)
                    .setTitle(R.string.reset_connections)
                    .setMessage(
                        getString(
                            R.string.ensure_close_all,
                            size.toString(),
                        )
                    )
                    .setPositiveButton(R.string.ok) { _, _ ->
                        it.connection.service?.resetNetwork()
                        snackbar(R.string.have_reset_network).show()
                    }
                    .setNegativeButton(R.string.no_thanks) { _, _ -> }
                    .show()
            }

            // Sort

            R.id.action_sort_ascending -> {
                DataStore.trafficDescending = false
                item.isChecked = true
            }

            R.id.action_sort_descending -> {
                DataStore.trafficDescending = true
                item.isChecked = true
            }

            R.id.action_sort_time -> {
                DataStore.trafficSortMode = TrafficSortMode.START
                item.isChecked = true
            }

            R.id.action_sort_inbound -> {
                DataStore.trafficSortMode = TrafficSortMode.INBOUND
                item.isChecked = true
            }

            R.id.action_sort_source -> {
                DataStore.trafficSortMode = TrafficSortMode.SRC
                item.isChecked = true
            }

            R.id.action_sort_destination -> {
                DataStore.trafficSortMode = TrafficSortMode.DST
                item.isChecked = true
            }

            R.id.action_sort_upload -> {
                DataStore.trafficSortMode = TrafficSortMode.UPLOAD
                item.isChecked = true
            }

            R.id.action_sort_download -> {
                DataStore.trafficSortMode = TrafficSortMode.DOWNLOAD
                item.isChecked = true
            }

            R.id.action_sort_rule -> {
                DataStore.trafficSortMode = TrafficSortMode.MATCHED_RULE
                item.isChecked = true
            }

            else -> return false
        }
        return true
    }

    fun emitStats(dashboardStatus: DashboardStatus) {
        when (binding.trafficPager.currentItem) {
            POSITION_DASH -> adapter.getCurrentFragment(POSITION_DASH).let { dashboard ->
                dashboard as DashboardFragment
                dashboard.emitStats(dashboardStatus.memory, dashboardStatus.goroutines)
            }

            POSITION_CONNECTIONS -> (adapter.getCurrentFragment(POSITION_CONNECTIONS) as ConnectionListFragment)
                .emitStats(dashboardStatus.connections)
        }
    }

    fun clashModeUpdate(mode: String) {
        if (binding.trafficPager.currentItem != POSITION_DASH) return
        (adapter.getCurrentFragment(POSITION_DASH) as DashboardFragment).clashModeUpdate(mode)
    }

    inner class TrafficAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int {
            return 2
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                POSITION_DASH -> DashboardFragment().also {
                    searchView.isVisible = false
                }

                POSITION_CONNECTIONS -> ConnectionListFragment().also {
                    searchView.isVisible = true
                }

                else -> throw IllegalArgumentException()
            }
        }

        fun getCurrentFragment(position: Int): Fragment? {
            return childFragmentManager.findFragmentByTag("f$position")
        }
    }

    private lateinit var searchView: SearchView

    override fun onQueryTextSubmit(query: String?): Boolean {
        (adapter.getCurrentFragment(POSITION_CONNECTIONS) as? ConnectionListFragment)
            ?.searchString = query
        return false
    }

    override fun onQueryTextChange(query: String?): Boolean {
        (adapter.getCurrentFragment(POSITION_CONNECTIONS) as? ConnectionListFragment)
            ?.searchString = query
        return false
    }

}