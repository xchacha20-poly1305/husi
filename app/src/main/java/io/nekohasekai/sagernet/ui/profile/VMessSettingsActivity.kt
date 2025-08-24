package io.nekohasekai.sagernet.ui.profile

import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

class VMessSettingsActivity : StandardV2RaySettingsActivity() {

    companion object {
        const val EXTRA_VLESS = "vless"
    }

    override val viewModel by viewModels<VMessSettingsViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val isVLESS = intent?.getBooleanExtra(EXTRA_VLESS, false) == true
                return VMessSettingsViewModel(isVLESS) as T
            }
        }
    }

}