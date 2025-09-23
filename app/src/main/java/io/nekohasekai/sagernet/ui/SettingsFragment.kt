package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.core.view.GravityCompat
import com.google.accompanist.themeadapter.material3.Mdc3Theme
import io.nekohasekai.sagernet.R

@OptIn(ExperimentalMaterial3Api::class)
class SettingsFragment : OnKeyDownFragment(R.layout.layout_config_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<ComposeView>(R.id.toolbar)
        toolbar.setContent {
            @Suppress("DEPRECATION")
            Mdc3Theme {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            (requireActivity() as MainActivity).binding
                                .drawerLayout.openDrawer(GravityCompat.START)
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.menu),
                            )
                        }
                    },
                )
            }
        }

        if (savedInstanceState == null) parentFragmentManager.beginTransaction()
            .replace(R.id.settings, SettingsPreferenceFragment())
            .commitAllowingStateLoss()
    }

}