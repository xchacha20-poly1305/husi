package io.nekohasekai.sagernet.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.ui.configuration.ConfigurationScreen

class SwitchActivity : ComposeActivity() {

    private val mainViewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                ConfigurationScreen(
                    mainViewModel = mainViewModel,
                    onNavigationClick = ::finish,
                    titleRes = R.string.action_switch,
                    selectCallback = ::returnProfile,
                    preSelected = null,
                )
            }
        }
    }

    private fun returnProfile(profileId: Long) {
        DataStore.selectedProxy = profileId
        repo.reloadService()
        finish()
    }
}