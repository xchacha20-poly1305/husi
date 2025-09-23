package io.nekohasekai.sagernet.ui

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.utils.Theme

abstract class ThemedActivity : AppCompatActivity {
    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    var themeResId = 0
    var uiMode = 0
    open val isDialog = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!isDialog) {
            Theme.apply(this)
        } else {
            Theme.applyDialog(this)
        }
        Theme.applyNightTheme()

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // https://stackoverflow.com/questions/79319740/edge-to-edge-doesnt-work-when-activity-recreated-or-appcompatdelegate-setdefaul
            // BAKLAVA and later VANILLA_ICE_CREAM have fixed this
            // set this before super.onCreate(savedInstanceState)
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val insetController = WindowCompat.getInsetsController(window, window.decorView)
            val usingNightMode = Theme.usingNightMode()
            insetController.isAppearanceLightNavigationBars = !usingNightMode
            // https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1576
            insetController.isAppearanceLightStatusBars = if (DataStore.appTheme == Theme.BLACK) {
                !usingNightMode
            } else {
                false
            }
        }

        uiMode = resources.configuration.uiMode

        onBackPressedCallback?.let {
            onBackPressedDispatcher.addCallback(this, it)
        }
    }

    override fun setTheme(resId: Int) {
        super.setTheme(resId)

        themeResId = resId
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.uiMode != uiMode) {
            uiMode = newConfig.uiMode
            ActivityCompat.recreate(this)
        }
    }

    fun snackbar(@StringRes resId: Int): Snackbar = snackbar("").setText(resId)
    fun snackbar(text: CharSequence): Snackbar = snackbarInternal(text).apply {
        view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).apply {
            maxLines = 10
        }
    }

    internal open fun snackbarInternal(text: CharSequence): Snackbar = throw NotImplementedError()

    open val onBackPressedCallback: OnBackPressedCallback? get() = null
}