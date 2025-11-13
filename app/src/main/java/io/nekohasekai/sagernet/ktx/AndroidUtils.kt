package io.nekohasekai.sagernet.ktx

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.text.format.DateUtils
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore

// Utils that require Android. Split it so that test can not include Android parts.

val isExpert: Boolean
    get() = BuildConfig.DEBUG || DataStore.isExpert

fun RecyclerView.scrollTo(index: Int, force: Boolean = false) {
    if (force) post {
        scrollToPosition(index)
    }
    postDelayed({
        try {
            layoutManager?.startSmoothScroll(object : LinearSmoothScroller(context) {
                init {
                    targetPosition = index
                }

                override fun getVerticalSnapPreference(): Int {
                    return SNAP_TO_START
                }
            })
        } catch (_: IllegalArgumentException) {
        }
    }, 300L)
}

@ColorInt
fun Context.getColour(@ColorRes colorRes: Int): Int {
    return ContextCompat.getColor(this, colorRes)
}

@ColorInt
fun Context.getColorAttr(@AttrRes resId: Int): Int {
    return ContextCompat.getColor(this, TypedValue().also {
        theme.resolveAttribute(resId, it, true)
    }.resourceId)
}

@JvmOverloads
fun DialogFragment.showAllowingStateLoss(fragmentManager: FragmentManager, tag: String? = null) {
    if (!fragmentManager.isStateSaved) show(fragmentManager, tag)
}

fun broadcastReceiver(callback: (Context, Intent) -> Unit): BroadcastReceiver =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = callback(context, intent)
    }

fun Context.listenForPackageChanges(onetime: Boolean = true, callback: () -> Unit) =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            callback()
            if (onetime) context.unregisterReceiver(this)
        }
    }.apply {
        registerReceiver(this, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        })
    }

/**
 * Based on: https://stackoverflow.com/a/26348729/2245107
 */
fun Resources.Theme.resolveResourceId(@AttrRes resId: Int): Int {
    val typedValue = TypedValue()
    if (!resolveAttribute(resId, typedValue, true)) throw Resources.NotFoundException()
    return typedValue.resourceId
}

/** Generate friendly and easy-understand message for failed URL test */
@StringRes
fun readableUrlTestError(error: String?): Int? {
    val lowercase = error?.lowercase() ?: return null
    return when {
        lowercase.contains("timeout") || lowercase.contains("deadline") -> {
            R.string.connection_test_timeout
        }

        lowercase.contains("refused") || lowercase.contains("closed pipe") || lowercase.contains("reset") -> {
            R.string.connection_test_refused
        }

        lowercase.contains("via clientconn.close") -> {
            R.string.connection_test_mux
        }

        else -> null
    }
}

fun Context.contentOrUnset(content: String): String {
    return content.blankAsNull() ?: getString(R.string.not_set)
}

fun Context.contentOrUnset(content: Int): String {
    return if (content <= 0) {
        getString(R.string.not_set)
    } else {
        content.toString()
    }
}

fun ClipboardManager.trySetPrimaryClip(clip: String): Boolean {
    return try {
        setPrimaryClip(ClipData.newPlainText(null, clip))
        true
    } catch (error: RuntimeException) {
        Logs.w(error)
        false
    }
}

fun ClipboardManager.first(): String? {
    return primaryClip?.getItemAt(0)?.text?.toString()
}

fun Context.formatTime(millis: Long): String {
    return DateUtils.getRelativeTimeSpanString(this, millis)
        // hack for Chinese to add space, "1月1日" -> "1 月 1 日","上午0:00" -> 上午 0:00"
        .replace("^([1-9]|1[0-2])月([1-9]|1[0-9]|2[0-9]|3[0-1])日+".toRegex(), "$1 月 $2 日")
        .replace("^上午(([1-9]|1[0-2]):([0-5][0-9]))+".toRegex(), "上午 $1")
        .replace("^下午(([1-9]|1[0-2]):([0-5][0-9]))+".toRegex(), "下午 $1")
}