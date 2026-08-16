package fr.husi.ui.openconnect

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.husi.resources.Res
import fr.husi.resources.auth_browser_cookie_missing
import fr.husi.resources.auth_browser_header_unsupported_android
import fr.husi.resources.close
import fr.husi.resources.openconnect_authentication
import fr.husi.vpn.OpenConnectBrowserCompletionMode
import fr.husi.vpn.OpenConnectBrowserRequestState
import fr.husi.vpn.OpenConnectBrowserResultState
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun PlatformOpenConnectBrowserDialog(
    challengeId: String,
    request: OpenConnectBrowserRequestState,
    visible: Boolean,
    onDismiss: () -> Unit,
    onResult: (OpenConnectBrowserResultState) -> Unit,
    onError: (String) -> Unit,
) {
    if (!visible) return
    val unsupportedMessage = stringResource(Res.string.auth_browser_header_unsupported_android)
    val missingCookieMessage = stringResource(Res.string.auth_browser_cookie_missing)
    val browser = remember(challengeId) {
        OpenConnectWebViewBrowser(
            request = request,
            unsupportedMessage = unsupportedMessage,
            missingCookieMessage = missingCookieMessage,
            onResult = onResult,
            onError = onError,
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.openconnect_authentication)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close))
                        }
                    },
                )
                AndroidView(
                    factory = { context -> WebView(context).also(browser::start) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    BackHandler(onBack = onDismiss)
    DisposableEffect(browser) {
        onDispose(browser::close)
    }
}

@Composable
internal actual fun PlatformOpenConnectBrowserAuthContent(
    request: OpenConnectBrowserRequestState,
    finalUrl: String,
    onFinalUrlChange: (String) -> Unit,
    cookies: SnapshotStateMap<String, String>,
    headers: SnapshotStateMap<String, String>,
    enabled: Boolean,
) = Unit

private class OpenConnectWebViewBrowser(
    private val request: OpenConnectBrowserRequestState,
    private val unsupportedMessage: String,
    private val missingCookieMessage: String,
    private val onResult: (OpenConnectBrowserResultState) -> Unit,
    private val onError: (String) -> Unit,
) {
    companion object {
        private const val COOKIE_RETRY_COUNT = 30
        private const val COOKIE_RETRY_DELAY_MILLIS = 100L
    }

    private var webView: WebView? = null
    private var completed = false
    private var closed = false

    @SuppressLint("SetJavaScriptEnabled")
    fun start(view: WebView) {
        if (request.completionMode == OpenConnectBrowserCompletionMode.Header) {
            fail(unsupportedMessage)
            return
        }
        if (request.completionMode == OpenConnectBrowserCompletionMode.Invalid) {
            fail(missingCookieMessage)
            return
        }

        webView = view
        CookieManager.getInstance().also { cookieManager ->
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(view, true)
        }
        view.settings.also { settings ->
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, navigationRequest: WebResourceRequest): Boolean =
                handleNavigation(view, navigationRequest.url.toString())

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = handleNavigation(view, url)

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (completeCallback(url)) view.stopLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (request.completionMode == OpenConnectBrowserCompletionMode.Cookie) {
                    completeWhenCookiesAvailable(view, url, 0)
                }
            }
        }
        view.loadUrl(request.url)
    }

    fun close() {
        if (closed) return
        closed = true
        webView?.also { view ->
            view.stopLoading()
            view.removeAllViews()
            view.destroy()
        }
        webView = null
    }

    private fun completeWhenCookiesAvailable(view: WebView, url: String, attempt: Int) {
        if (completed || closed || webView !== view) return
        val cookies = requestedCookies(url)
        if (completeEarlyCookie(cookies)) return
        if (url != request.finalUrl) return
        val finalCookies = cookies.filterKeys { it in request.cookieNames && cookies[it].orEmpty().isNotEmpty() }
        if (finalCookies.isNotEmpty()) {
            completed = true
            onResult(OpenConnectBrowserResultState(url, finalCookies, emptyMap()))
            return
        }
        if (attempt >= COOKIE_RETRY_COUNT) {
            fail(missingCookieMessage)
            return
        }
        view.postDelayed(
            { completeWhenCookiesAvailable(view, url, attempt + 1) },
            COOKIE_RETRY_DELAY_MILLIS,
        )
    }

    private fun handleNavigation(view: WebView, url: String): Boolean {
        if (
            request.completionMode == OpenConnectBrowserCompletionMode.Cookie &&
            completeEarlyCookie(requestedCookies(view.url.orEmpty()))
        ) {
            view.stopLoading()
            return true
        }
        return completeCallback(url)
    }

    private fun completeCallback(url: String): Boolean {
        if (completed || closed || request.completionMode != OpenConnectBrowserCompletionMode.Callback) return false
        if (request.callbackUrlPrefixes.none { prefix -> url.startsWith(prefix) }) return false
        completed = true
        onResult(OpenConnectBrowserResultState(url, emptyMap(), emptyMap()))
        return true
    }

    private fun completeEarlyCookie(cookies: Map<String, String>): Boolean {
        for (name in request.earlyCookieNames) {
            val value = cookies[name].orEmpty()
            if (value.isEmpty()) continue
            completed = true
            onResult(OpenConnectBrowserResultState("", mapOf(name to value), emptyMap()))
            return true
        }
        return false
    }

    private fun requestedCookies(url: String): Map<String, String> {
        val requestedNames = (request.cookieNames + request.earlyCookieNames).toSet()
        return CookieManager.getInstance().getCookie(url)
            .orEmpty()
            .split(';')
            .mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val name = entry.substring(0, separator).trim()
                if (name !in requestedNames) return@mapNotNull null
                name to entry.substring(separator + 1)
            }
            .toMap()
    }

    private fun fail(message: String) {
        if (completed || closed) return
        completed = true
        onError(message)
    }
}
