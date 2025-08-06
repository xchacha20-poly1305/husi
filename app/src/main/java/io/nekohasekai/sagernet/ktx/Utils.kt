@file:SuppressLint("SoonBlockedPrivateApi")

package io.nekohasekai.sagernet.ktx

import android.annotation.SuppressLint
import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.Closeable
import java.io.FileDescriptor
import java.net.InetAddress
import java.net.Socket
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

/**
 * https://android.googlesource.com/platform/prebuilts/runtime/+/94fec32/appcompat/hiddenapi-light-greylist.txt#9466
 */

private val socketGetFileDescriptor = Socket::class.java.getDeclaredMethod("getFileDescriptor\$")
val Socket.fileDescriptor get() = socketGetFileDescriptor.invoke(this) as FileDescriptor

private val getInt = FileDescriptor::class.java.getDeclaredMethod("getInt$")
val FileDescriptor.int get() = getInt.invoke(this) as Int

fun parsePort(str: String?, default: Int, min: Int = 1025): Int {
    val value = str?.toIntOrNull() ?: default
    return if (value < min || value > 65535) default else value
}

/**
 * A slightly more performant variant of parseNumericAddress.
 *
 * Bug in Android 9.0 and lower: https://issuetracker.google.com/issues/123456213
 */

private val parseNumericAddress by lazy {
    InetAddress::class.java.getDeclaredMethod("parseNumericAddress", String::class.java).apply {
        isAccessible = true
    }
}

fun String?.parseNumericAddress(): InetAddress? =
    Os.inet_pton(OsConstants.AF_INET, this) ?: Os.inet_pton(OsConstants.AF_INET6, this)?.let {
        if (Build.VERSION.SDK_INT >= 29) it else parseNumericAddress.invoke(
            null, this
        ) as InetAddress
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
    thisRef: Any?, property: KProperty<*>, value: F
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

/**
 * Designed to replace map(), which only distribute 10 as initial length.
 */
fun <T, R> Collection<T>.mapX(transform: (T) -> R): List<R> {
    val list = ArrayList<R>(size)
    for (item in this) {
        list.add(transform(item))
    }
    return list
}

fun <K, V, T> Map<K, V>.mapX(transform: (Map.Entry<K, V>) -> T): List<T> {
    val list = ArrayList<T>(size)
    for (item in this) {
        list.add(transform(item))
    }
    return list
}

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