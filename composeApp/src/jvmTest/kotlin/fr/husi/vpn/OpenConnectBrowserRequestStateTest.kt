package fr.husi.vpn

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenConnectBrowserRequestStateTest {

    @Test
    fun `callback request builds URL-only result`() {
        val request = request(callbackUrlPrefixes = listOf("http://127.0.0.1:"))

        assertEquals(OpenConnectBrowserCompletionMode.Callback, request.completionMode)
        assertEquals(
            OpenConnectBrowserResultState("http://127.0.0.1:1234/callback", emptyMap(), emptyMap()),
            request.buildResult(
                finalUrl = "http://127.0.0.1:1234/callback",
                cookies = mapOf("ignored" to "value"),
                headers = mapOf("ignored" to "value"),
            ),
        )
    }

    @Test
    fun `early cookie builds empty URL single-cookie result`() {
        val request = request(
            finalUrl = "https://vpn.example.com/final",
            cookieNames = listOf("webvpn"),
            earlyCookieNames = listOf("sso-error"),
        )

        assertEquals(OpenConnectBrowserCompletionMode.Cookie, request.completionMode)
        assertEquals(
            OpenConnectBrowserResultState("", mapOf("sso-error" to "denied"), emptyMap()),
            request.buildResult(
                finalUrl = request.finalUrl,
                cookies = mapOf("webvpn" to "token", "sso-error" to "denied"),
                headers = emptyMap(),
            ),
        )
    }

    @Test
    fun `final cookie result excludes early cookies`() {
        val request = request(
            finalUrl = "https://vpn.example.com/final",
            cookieNames = listOf("webvpn"),
            earlyCookieNames = listOf("sso-error"),
        )

        assertEquals(
            OpenConnectBrowserResultState(
                request.finalUrl,
                mapOf("webvpn" to "token"),
                emptyMap(),
            ),
            request.buildResult(
                finalUrl = request.finalUrl,
                cookies = mapOf("webvpn" to "token", "sso-error" to ""),
                headers = emptyMap(),
            ),
        )
    }

    @Test
    fun `header request selects the header mode`() {
        assertEquals(
            OpenConnectBrowserCompletionMode.Header,
            request(headerNames = listOf("X-Auth")).completionMode,
        )
    }

    @Test
    fun `a request the core would reject is invalid`() {
        assertEquals(
            OpenConnectBrowserCompletionMode.Invalid,
            request().completionMode,
            "no completion mode at all",
        )
        assertEquals(
            OpenConnectBrowserCompletionMode.Invalid,
            request(url = "", callbackUrlPrefixes = listOf("http://127.0.0.1:")).completionMode,
            "a request without its login URL",
        )
        assertEquals(
            OpenConnectBrowserCompletionMode.Invalid,
            request(
                finalUrl = "https://vpn.example.com/final",
                callbackUrlPrefixes = listOf("http://127.0.0.1:"),
            ).completionMode,
            "two completion modes at once",
        )
        assertEquals(
            OpenConnectBrowserCompletionMode.Invalid,
            request(callbackUrlPrefixes = listOf("http://127.0.0.1:", "http://127.0.0.1:")).completionMode,
            "a duplicate callback prefix",
        )
        assertEquals(
            OpenConnectBrowserCompletionMode.Invalid,
            request(cookieNames = listOf("webvpn")).completionMode,
            "cookie mode without its final URL",
        )
        assertEquals(
            OpenConnectBrowserCompletionMode.Invalid,
            request(
                finalUrl = "https://vpn.example.com/final",
                cookieNames = listOf("webvpn"),
                earlyCookieNames = listOf("webvpn"),
            ).completionMode,
            "an early cookie repeated in the final cookie names",
        )
        assertEquals(
            OpenConnectBrowserCompletionMode.Invalid,
            request(headerNames = listOf("X-Auth", "x-auth")).completionMode,
            "a duplicate header name, which HTTP compares case-insensitively",
        )
    }

    private fun request(
        url: String = "https://login.example.com",
        finalUrl: String = "",
        cookieNames: List<String> = emptyList(),
        earlyCookieNames: List<String> = emptyList(),
        headerNames: List<String> = emptyList(),
        callbackUrlPrefixes: List<String> = emptyList(),
    ) = OpenConnectBrowserRequestState(
        url = url,
        finalUrl = finalUrl,
        cacheId = "test",
        cookieNames = cookieNames,
        earlyCookieNames = earlyCookieNames,
        headerNames = headerNames,
        callbackUrlPrefixes = callbackUrlPrefixes,
    )
}
