package io.nekohasekai.sagernet.ktx

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.jakewharton.processphoenix.ProcessPhoenix
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.bg.Executable
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.PublicDatabase
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.delay
import androidx.core.view.isVisible
import androidx.core.view.isGone
import androidx.preference.ListPreference
import androidx.preference.Preference
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

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

val Context.shortAnimTime
    get() = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

fun View.crossFadeFrom(other: View) {
    clearAnimation()
    other.clearAnimation()
    if (isVisible && other.isGone) return
    alpha = 0F
    visibility = View.VISIBLE
    animate().alpha(1F).duration = context.shortAnimTime
    other.animate().alpha(0F).setListener(object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            other.visibility = View.GONE
        }
    }).duration = context.shortAnimTime
}


fun Fragment.snackbar(textId: Int) = (requireActivity() as MainActivity).snackbar(textId)
fun Fragment.snackbar(text: CharSequence) = (requireActivity() as MainActivity).snackbar(text)

fun ThemedActivity.startFilesForResult(
    launcher: ActivityResultLauncher<String>, input: String,
) {
    try {
        return launcher.launch(input)
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
    snackbar(getString(R.string.file_manager_missing)).show()
}

fun Fragment.startFilesForResult(
    launcher: ActivityResultLauncher<String>, input: String,
) {
    try {
        return launcher.launch(input)
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
    (requireActivity() as ThemedActivity).snackbar(getString(R.string.file_manager_missing)).show()
}

fun Fragment.needReload() {
    if (DataStore.serviceState.started) {
        snackbar(getString(R.string.need_reload)).setAction(R.string.apply) {
            repo.reloadService()
        }.show()
    }
}

fun Fragment.needRestart() {
    snackbar(R.string.need_restart).setAction(R.string.apply) {
        repo.stopService()
        val ctx = requireContext()
        runOnDefaultDispatcher {
            delay(500)
            SagerDatabase.instance.close()
            PublicDatabase.instance.close()
            Executable.killAll(true)
            ProcessPhoenix.triggerRebirth(ctx, Intent(ctx, MainActivity::class.java))
        }
    }.show()
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

fun Preference.remove() = parent!!.removePreference(this)

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

/**
 * If the summary has a [java.lang.String.format] String formatting} marker in it,
 * (i.e. "%s" or "%1$s"), then the current entry value will be substituted in its place.
 * @see [androidx.preference.ListPreference.getSummary]
 */
fun ListPreference.setSummaryUserInput(userInput: String) {
    summary = userInput.replace("%", "%%")
}

fun EditText.textChanges(): Flow<Editable?> {
    return callbackFlow {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                trySend(s)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        addTextChangedListener(watcher)
        awaitClose { removeTextChangedListener(watcher) }
    }
}

fun Context.contentOrUnset(content: String): String {
    return content.blankAsNull() ?: getString(androidx.preference.R.string.not_set)
}

fun Context.contentOrUnset(content: Int): String {
    return if (content <= 0) {
        getString(androidx.preference.R.string.not_set)
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