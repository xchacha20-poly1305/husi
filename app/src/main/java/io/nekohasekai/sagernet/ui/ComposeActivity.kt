package io.nekohasekai.sagernet.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import io.nekohasekai.sagernet.compose.theme.isDarkMode
import io.nekohasekai.sagernet.database.DataStore

open class ComposeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // https://stackoverflow.com/questions/79319740/edge-to-edge-doesnt-work-when-activity-recreated-or-appcompatdelegate-setdefaul
            // BAKLAVA and later VANILLA_ICE_CREAM have fixed this
            // set this before super.onCreate(savedInstanceState)
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val insetController = WindowCompat.getInsetsController(window, window.decorView)
            val usingNightMode = resources.isDarkMode(DataStore.nightTheme)
            // https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1576
            insetController.isAppearanceLightNavigationBars = !usingNightMode
            insetController.isAppearanceLightStatusBars = !usingNightMode
        }
    }
}