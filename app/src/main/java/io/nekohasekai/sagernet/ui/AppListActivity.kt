package io.nekohasekai.sagernet.ui

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.databinding.LayoutAppListBinding
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher

class AppListActivity : AbstractAppListActivity() {

    override val viewModel by viewModels<AppListActivityViewModel>()
    private lateinit var binding: LayoutAppListBinding
    override val root get() = binding.root
    override val toolbarTitle get() = R.string.select_apps
    override val toolbar get() = binding.toolbar
    override val collapsingToolbar get() = binding.collapsingToolbar
    override val search get() = binding.search
    override val showSystemApps get() = binding.showSystemApps
    override val loading get() = binding.loading
    override val list get() = binding.list
    override val snackbarAnchor get() = binding.list

    override fun bind(layoutInflater: LayoutInflater) {
        binding = LayoutAppListBinding.inflate(layoutInflater)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_invert_selections -> {
            runOnDefaultDispatcher {
                viewModel.invertSelected()
            }
            true
        }

        R.id.action_clear_selections -> {
            runOnDefaultDispatcher {
                viewModel.clearSelections()
            }
            true
        }

        R.id.action_export_clipboard -> {
            val body = viewModel.packages.joinToString("\n")
            val success = SagerNet.trySetPrimaryClip("false\n$body")
            Snackbar.make(
                binding.list,
                if (success) R.string.action_export_msg else R.string.action_export_err,
                Snackbar.LENGTH_LONG
            ).show()
            true
        }

        R.id.action_import_clipboard -> {
            val clipBoardString = SagerNet.clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            runOnDefaultDispatcher {
                viewModel.importFromClipboard(clipBoardString)
            }
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

}
