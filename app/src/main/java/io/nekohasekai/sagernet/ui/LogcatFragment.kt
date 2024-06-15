package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutLogcatBinding
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.utils.SendLog
import io.nekohasekai.sfa.utils.ColorUtils
import io.nekohasekai.sfa.utils.ColorUtils.highlightKeyword
import libcore.Libcore
import moe.matsuri.nb4a.utils.setOnFocusCancel

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    Toolbar.OnMenuItemClickListener,
    SearchView.OnQueryTextListener {

    lateinit var binding: LayoutLogcatBinding

    @SuppressLint("RestrictedApi", "WrongConstant")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_log)

        toolbar.inflateMenu(R.menu.logcat_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutLogcatBinding.bind(view)

        if (Build.VERSION.SDK_INT >= 23) {
            binding.textview.breakStrategy = 0 // simple
        }

        reloadSession()

        searchView = toolbar.findViewById<SearchView>(R.id.action_log_search).apply {
            setOnQueryTextListener(this@LogcatFragment)
            setOnFocusCancel()
        }
    }

    private fun reloadSession() {
        binding.textview.text = ColorUtils.ansiEscapeToSpannable(
            binding.root.context, String(SendLog.getCoreLog(50 * 1024))
        ).run {
            if (!searchKeyword.isNullOrBlank()) {
                highlightKeyword(
                    this,
                    searchKeyword!!,
                    ContextCompat.getColor(binding.root.context, R.color.material_amber_400),
                )
            }
            this
        }

        binding.scroolview.post {
            binding.scroolview.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_logcat -> {
                searchKeyword = null
                runOnDefaultDispatcher {
                    try {
                        Libcore.logClear()
                        Runtime.getRuntime().exec("/system/bin/logcat -c")
                    } catch (e: Exception) {
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                        return@runOnDefaultDispatcher
                    }
                    onMainDispatcher {
                        binding.textview.text = ""
                    }
                }

            }

            R.id.action_send_logcat -> {
                val context = requireContext()
                runOnDefaultDispatcher {
                    SendLog.sendLog(context, "husi")
                }
            }

            R.id.action_refresh -> {
                reloadSession()
            }
        }

        return true
    }

    private lateinit var searchView: SearchView
    private var searchKeyword: String? = null

    override fun onQueryTextSubmit(query: String?): Boolean {
        searchKeyword = query
        reloadSession()
        return false
    }

    override fun onQueryTextChange(newText: String): Boolean {
        return false
    }

}