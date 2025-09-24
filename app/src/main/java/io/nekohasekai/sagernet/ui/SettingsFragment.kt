package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.core.view.GravityCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.SimpleTopAppBar
import io.nekohasekai.sagernet.compose.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
class SettingsFragment : OnKeyDownFragment(R.layout.layout_config_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<ComposeView>(R.id.toolbar)
        toolbar.setContent {
            @Suppress("DEPRECATION")
            AppTheme {
                SimpleTopAppBar(
                    title = R.string.settings,
                    navigationIcon = Icons.Filled.Menu,
                    navigationDescription = stringResource(R.string.menu),
                ) {
                    (requireActivity() as MainActivity).binding
                        .drawerLayout.openDrawer(GravityCompat.START)
                }
            }
        }

        if (savedInstanceState == null) parentFragmentManager.beginTransaction()
            .replace(R.id.settings, SettingsPreferenceFragment())
            .commitAllowingStateLoss()
    }

}