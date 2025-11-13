package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.theme.AppTheme
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.repository.repo
import io.nekohasekai.sagernet.ui.configuration.ConfigurationScreen

class SwitchActivity : ComposeActivity() {

    private val mainViewModel by viewModels<MainViewModel>()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val sheetState = rememberStandardBottomSheetState(
                    initialValue = SheetValue.PartiallyExpanded,
                    confirmValueChange = {
                        if (it == SheetValue.Hidden) {
                            finish()
                            false
                        } else {
                            true
                        }
                    },
                )
                val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

                val density = LocalDensity.current
                val windowHeight = with(density) {
                    LocalWindowInfo.current.containerSize.height.toDp()
                }

                LaunchedEffect(sheetState) {
                    snapshotFlow { sheetState.currentValue }
                        .collect { value ->
                            if (value == SheetValue.Expanded) {
                                startActivity(Intent(this@SwitchActivity, MainActivity::class.java))
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    overrideActivityTransition(
                                        OVERRIDE_TRANSITION_OPEN,
                                        android.R.anim.fade_in,
                                        android.R.anim.fade_out,
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    overridePendingTransition(
                                        android.R.anim.fade_in,
                                        android.R.anim.fade_out,
                                    )
                                }
                                finish()
                            }
                        }
                }

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = windowHeight * 0.6f,
                    containerColor = Color.Transparent,
                    sheetContent = {
                        ConfigurationScreen(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(windowHeight),
                            mainViewModel = mainViewModel,
                            onNavigationClick = ::finish,
                            titleRes = R.string.action_switch,
                            selectCallback = ::returnProfile,
                            preSelected = null,
                            fillMaxSize = false,
                        )
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = ::finish,
                            ),
                    )
                }
            }
        }
    }

    private fun returnProfile(profileId: Long) {
        DataStore.selectedProxy = profileId
        repo.reloadService()
        finish()
    }
}
