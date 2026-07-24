package fr.husi.ui.openconnect

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

    private fun request(
        finalUrl: String = "",
        cookieNames: List<String> = emptyList(),
        earlyCookieNames: List<String> = emptyList(),
        headerNames: List<String> = emptyList(),
        callbackUrlPrefixes: List<String> = emptyList(),
    ) = OpenConnectBrowserRequestState(
        url = "https://login.example.com",
        finalUrl = finalUrl,
        cacheId = "test",
        cookieNames = cookieNames,
        earlyCookieNames = earlyCookieNames,
        headerNames = headerNames,
        callbackUrlPrefixes = callbackUrlPrefixes,
    )
}
