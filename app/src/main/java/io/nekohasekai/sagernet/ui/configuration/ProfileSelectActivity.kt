package io.nekohasekai.sagernet.ui.configuration

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.ui.ComposeActivity
import io.nekohasekai.sagernet.ui.MainViewModel

class ProfileSelectActivity : ComposeActivity() {

    companion object {
        const val EXTRA_SELECTED = "selected"
        const val EXTRA_PROFILE_ID = "id"
    }

    private val mainViewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selected = intent.getLongExtra(EXTRA_SELECTED, -1).takeIf { it > 0 }

        setContent {
            AppTheme {
                ConfigurationScreen(
                    mainViewModel = mainViewModel,
                    selectCallback = this::returnProfile,
                    onNavigationClick = ::finish,
                    titleRes = R.string.select_profile,
                    preSelected = selected,
                )
            }
        }
    }

    private fun returnProfile(profileId: Long) {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_PROFILE_ID, profileId),
        )
        finish()
    }

}