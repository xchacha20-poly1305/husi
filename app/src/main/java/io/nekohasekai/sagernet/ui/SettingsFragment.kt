package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.nekohasekai.sagernet.R

class SettingsFragment : ToolbarFragment(R.layout.layout_config_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setTitle(R.string.settings)

        if (savedInstanceState == null) parentFragmentManager.beginTransaction()
            .replace(R.id.settings, SettingsPreferenceFragment())
            .commitAllowingStateLoss()
    }

}