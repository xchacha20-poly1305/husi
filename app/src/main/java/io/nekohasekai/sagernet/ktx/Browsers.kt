package io.nekohasekai.sagernet.ktx

import android.content.Context
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

fun Context.launchCustomTab(link: String) {
    CustomTabsIntent.Builder().apply {
        setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
        setColorSchemeParams(
            CustomTabsIntent.COLOR_SCHEME_LIGHT,
            CustomTabColorSchemeParams.Builder()
                .setToolbarColor(getColorAttr(androidx.appcompat.R.attr.colorPrimary))
                .build(),
        )
        setColorSchemeParams(
            CustomTabsIntent.COLOR_SCHEME_DARK,
            CustomTabColorSchemeParams.Builder()
                .setToolbarColor(getColorAttr(androidx.appcompat.R.attr.colorPrimary))
                .build(),
        )
    }.build().apply {
        if (intent.resolveActivity(packageManager) != null) {
            launchUrl(this@launchCustomTab, link.toUri())
        }
    }
}