
package fr.husi.ktx

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import fr.husi.BuildConfig
import fr.husi.database.DataStore
import fr.husi.repository.repo
import fr.husi.resources.Res
import fr.husi.resources.connection_test_mux
import fr.husi.resources.connection_test_refused
import fr.husi.resources.connection_test_timeout
import fr.husi.resources.not_set
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.io.Closeable
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

inline fun <T> Iterable<T>.forEachTry(action: (T) -> Unit) {
    var result: Exception? = null
    for (element in this) try {
        action(element)
    } catch (e: Exception) {
        if (result == null) result = e else result.addSuppressed(e)
    }
    if (result != null) {
        throw result
    }
}

val Throwable.readableMessage: String
    get() = localizedMessage.takeIf { !it.isNullOrBlank() } ?: javaClass.simpleName

fun parsePort(str: String?, default: Int, min: Int = 1025): Int {
    val value = str?.toIntOrNull() ?: default
    return if (value < min || value > 65535) default else value
}

fun String.pathSafe(): String {
    // " " encoded as +
    return URLEncoder.encode(this, "UTF-8")
}

fun String.urlSafe(): String {
    return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}

fun String.unUrlSafe(): String {
    return try {
        URLDecoder.decode(this, "UTF-8")
    } catch (_: Exception) {
        this
    }
}

fun <T> Continuation<T>.tryResume(value: T) {
    try {
        resumeWith(Result.success(value))
    } catch (_: IllegalStateException) {
    }
}

fun <T> Continuation<T>.tryResumeWithException(exception: Throwable) {
    try {
        resumeWith(Result.failure(exception))
    } catch (_: IllegalStateException) {
    }
}

operator fun <F> KProperty0<F>.getValue(thisRef: Any?, property: KProperty<*>): F = get()
operator fun <F> KMutableProperty0<F>.setValue(
    thisRef: Any?, property: KProperty<*>, value: F,
) = set(value)

operator fun AtomicBoolean.getValue(thisRef: Any?, property: KProperty<*>): Boolean = get()
operator fun AtomicBoolean.setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
    set(value)

operator fun AtomicInteger.getValue(thisRef: Any?, property: KProperty<*>): Int = get()
operator fun AtomicInteger.setValue(thisRef: Any?, property: KProperty<*>, value: Int) = set(value)

operator fun AtomicLong.getValue(thisRef: Any?, property: KProperty<*>): Long = get()
operator fun AtomicLong.setValue(thisRef: Any?, property: KProperty<*>, value: Long) = set(value)

operator fun <T> AtomicReference<T>.getValue(thisRef: Any?, property: KProperty<*>): T = get()
operator fun <T> AtomicReference<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) =
    set(value)


fun String?.blankAsNull(): String? = if (isNullOrBlank()) null else this

fun String?.emptyAsNull(): String? = if (isNullOrEmpty()) null else this

/**
 * Returns the first non-default value from the provided getters.
 *
 * This function iterates over the provided getter functions and returns the first value
 * that is not equal to the default value. If all values are equal to the default value,
 * the default value is returned.
 *
 * Inspired by Go cmp.Or()
 *
 * @param default The default value to compare against.
 * @param getters A variable number of getter functions that return values to be compared.
 * @return The first non-default value from the getters, or the default value if all are equal to the default.
 */
fun <T> defaultOr(default: T, vararg getters: () -> T?): T {
    for (getter in getters) {
        val it = getter()
        if (it?.equals(default) == false) {
            return it
        }
    }
    return default
}

fun String.listByLineOrComma(): List<String> {
    return splitToSequence(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }.toList()
}

fun Closeable.closeQuietly() {
    try {
        close()
    } catch (rethrown: RuntimeException) {
        throw rethrown
    } catch (_: Exception) {
    }
}

fun String.sha256Hex(): String = toByteArray().sha256Hex()

fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") {
        "%02x".format(it)
    }

inline fun <E> MutableList<E>.removeFirstMatched(match: (E) -> Boolean): E? {
    val index = indexOfFirst(match)
    return if (index >= 0) {
        removeAt(index)
    } else {
        null
    }
}

fun intListN(length: Int) = List(length) { it }

suspend fun SnackbarHostState.showAndDismissOld(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = if (actionLabel == null) {
        SnackbarDuration.Short
    } else {
        SnackbarDuration.Indefinite
    },
): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(message, actionLabel, withDismissAction, duration)
}

val isExpert: Boolean
    get() = BuildConfig.DEBUG || DataStore.isExpert

/** Generate friendly and easy-understand message for failed URL test */
fun readableUrlTestError(error: String?): StringResource? {
    val lowercase = error?.lowercase() ?: return null
    return when {
        lowercase.contains("timeout") || lowercase.contains("deadline") -> {
            Res.string.connection_test_timeout
        }

        lowercase.contains("refused") || lowercase.contains("closed pipe") || lowercase.contains("reset") -> {
            Res.string.connection_test_refused
        }

        lowercase.contains("via clientconn.close") -> {
            Res.string.connection_test_mux
        }

        else -> null
    }
}

@Composable
fun contentOrUnset(content: String): String {
    return content.blankAsNull() ?: stringResource(Res.string.not_set)
}

suspend fun contentOrNotSet(content: String): String {
    return content.blankAsNull() ?: repo.getString(Res.string.not_set)
}

@Composable
fun contentOrUnset(content: Int): String {
    return if (content <= 0) {
        stringResource(Res.string.not_set)
    } else {
        content.toString()
    }
}

suspend fun contentOrNotSet(content: Int): String {
    return if (content <= 0) {
        repo.getString(Res.string.not_set)
    } else {
        content.toString()
    }
}

