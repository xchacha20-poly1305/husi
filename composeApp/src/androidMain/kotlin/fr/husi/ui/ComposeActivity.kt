package fr.husi.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import fr.husi.compose.theme.isDarkMode
import fr.husi.database.DataStore

open class ComposeActivity : PrivacyModeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val usingNightMode = resources.isDarkMode(DataStore.nightTheme.getBlocking())
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // https://stackoverflow.com/questions/79319740/edge-to-edge-doesnt-work-when-activity-recreated-or-appcompatdelegate-setdefaul
            // BAKLAVA and later VANILLA_ICE_CREAM have fixed this
            // set this before super.onCreate(savedInstanceState)
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        val style = if (usingNightMode) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val insetController = WindowCompat.getInsetsController(window, window.decorView)
            // https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1576
            insetController.isAppearanceLightNavigationBars = !usingNightMode
            insetController.isAppearanceLightStatusBars = !usingNightMode
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            keepTouchModeFocusOutOfCompose()
        }
    }

    /**
     * Before Android P, the window hands focus to its first focusable view even in touch mode: on
     * the first layout, and again whenever a view clears its focus. Compose forwards that focus to
     * its first focusable node, such as a search field, which then opens the keyboard or expands
     * the search bar on its own. Letting the content frame hold that focus keeps Compose out of it.
     *
     * https://issuetracker.google.com/issues/318968220
     * https://issuetracker.google.com/issues/433382598
     */
    private fun keepTouchModeFocusOutOfCompose() {
        findViewById<ViewGroup>(android.R.id.content).also {
            it.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            it.isFocusableInTouchMode = true
        }
    }
}
