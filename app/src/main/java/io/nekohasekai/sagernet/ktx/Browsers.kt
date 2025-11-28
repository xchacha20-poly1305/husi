package io.nekohasekai.sagernet.ktx

import android.content.Context
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import io.nekohasekai.sagernet.compose.theme.getPrimaryColor

fun Context.launchCustomTab(link: String) {
    val primaryColor = getPrimaryColor()
    CustomTabsIntent.Builder().apply {
        setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
        setColorSchemeParams(
            CustomTabsIntent.COLOR_SCHEME_LIGHT,
            CustomTabColorSchemeParams.Builder()
                .setToolbarColor(primaryColor)
                .build(),
        )
        setColorSchemeParams(
            CustomTabsIntent.COLOR_SCHEME_DARK,
            CustomTabColorSchemeParams.Builder()
                .setToolbarColor(primaryColor)
                .build(),
        )
    }.build().apply {
        if (intent.resolveActivity(packageManager) != null) {
            launchUrl(this@launchCustomTab, link.toUri())
        }
    }
}