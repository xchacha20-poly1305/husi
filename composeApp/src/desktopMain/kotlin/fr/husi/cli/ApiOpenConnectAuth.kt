package fr.husi.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import com.sun.net.httpserver.HttpServer
import fr.husi.APP_NAME
import fr.husi.core.CoreClient
import fr.husi.ktx.isLoopbackHost
import fr.husi.ktx.openUri
import fr.husi.ktx.readableMessage
import fr.husi.ktx.unwrapIPV6Host
import fr.husi.ktx.wrapIPV6Host
import fr.husi.proto.daemon.OpenConnectBrowserResult
import fr.husi.proto.daemon.openConnectAuthFormResponse
import fr.husi.proto.daemon.openConnectAuthResponseSubmission
import fr.husi.proto.daemon.openConnectBrowserCookie
import fr.husi.proto.daemon.openConnectBrowserResult
import fr.husi.vpn.OPENCONNECT_FIELD_PASSWORD
import fr.husi.vpn.OPENCONNECT_FIELD_SELECT
import fr.husi.vpn.OPENCONNECT_FIELD_TEXT
import fr.husi.vpn.OPENCONNECT_STATE_AUTH_PENDING
import fr.husi.vpn.OPENCONNECT_STATE_CONNECTED
import fr.husi.vpn.OPENCONNECT_STATE_ERROR
import fr.husi.vpn.OpenConnectAuthChallengeState
import fr.husi.vpn.OpenConnectAuthChoice
import fr.husi.vpn.OpenConnectAuthField
import fr.husi.vpn.OpenConnectBrowserCompletionMode
import fr.husi.vpn.OpenConnectBrowserRequestState
import fr.husi.vpn.OpenConnectEndpointState
import fr.husi.vpn.toState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.selects.select
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI

private const val OPENCONNECT_NAME = "openconnect"
private const val DEFAULT_CALLBACK_PORT = 8020
private const val LOOPBACK_LISTEN_ADDRESS = "127.0.0.1"

private const val CALLBACK_PAGE = """<!DOCTYPE html>
<html lang="en">
<head><meta charset="utf-8"><title>single sign-on</title></head>
<body style="font-family:system-ui,sans-serif;text-align:center;margin-top:4em">
<h3>Single sign-on completed</h3>
<p>You may close this tab and return to the terminal.</p>
</body>
</html>
"""

internal class ApiOpenConnectCommand : CliktCommand(OPENCONNECT_NAME) {
    init {
        subcommands(
            ApiVpnStatusCommand(OpenConnectAuthProtocol()),
            ApiOpenConnectAuthCommand(),
            ApiVpnCancelCommand(OpenConnectAuthProtocol()),
        )
    }

    override fun help(context: Context) = "Manage OpenConnect authentication"

    override fun run() = Unit
}

private class ApiOpenConnectAuthCommand : ApiVpnAuthCommand<OpenConnectEndpointState>(
    OPENCONNECT_NAME,
) {
    private val callbackPort by option(
        "--callback-port",
        help = "Local port for the browser single sign-on callback listener",
    ).int().restrictTo(1..65535).default(DEFAULT_CALLBACK_PORT)

    override fun help(context: Context) =
        "Answer OpenConnect authentication challenges.\n\nCtrl-C leaves the challenge pending: run the command again to resume, or use `cancel` to restart authentication."

    override fun protocol() = OpenConnectAuthProtocol(callbackPort)
}

internal class OpenConnectAuthProtocol(
    private val callbackPort: Int = DEFAULT_CALLBACK_PORT,
) : VpnAuthProtocol<OpenConnectEndpointState> {
    override val name = OPENCONNECT_NAME

    override val canceledMessage =
        "authentication challenge canceled; the client will restart authentication"

    override fun subscribe(client: CoreClient): Flow<List<VpnEndpointView<OpenConnectEndpointState>>> =
        client.subscribeOpenConnectStatus().map { update ->
            update.endpointsList.map { status ->
                val endpointState = status.toState()
                VpnEndpointView(
                    tag = endpointState.tag,
                    phase = openConnectPhase(endpointState.state),
                    state = endpointState.state,
                    error = endpointState.error,
                    challengeId = endpointState.authChallenge?.id,
                    endpointState = endpointState,
                )
            }
        }

    override fun describe(
        block: BlockWriter,
        endpoint: VpnEndpointView<OpenConnectEndpointState>,
    ) {
        val challenge = endpoint.endpointState.authChallenge
        val tunnelInfo = endpoint.endpointState.tunnelInfo
        when {
            challenge != null -> {
                block.addLine("Challenge", challengeSummary(challenge))
                if (challenge.message.isNotEmpty()) block.addLine("Message", challenge.message)
                if (challenge.error.isNotEmpty()) block.addLine("Error", challenge.error)
            }

            tunnelInfo != null -> {
                block.addLine("Server", tunnelInfo.server)
                block.addLine("Flavor", tunnelInfo.flavor)
                block.addLine("Transport", tunnelInfo.transport)
                if (tunnelInfo.ipv4.isNotEmpty()) block.addLine("IPv4", tunnelInfo.ipv4.joinToString(", "))
                if (tunnelInfo.ipv6.isNotEmpty()) block.addLine("IPv6", tunnelInfo.ipv6.joinToString(", "))
                if (tunnelInfo.dns.isNotEmpty()) block.addLine("DNS", tunnelInfo.dns.joinToString(", "))
                if (tunnelInfo.mtu > 0) block.addLine("MTU", tunnelInfo.mtu.toString())
                block.addLine("Connected since", formatVpnConnectedSince(tunnelInfo.connectedSince))
            }

            endpoint.phase == VpnEndpointPhase.Error -> block.addLine("Error", endpoint.error)
        }
    }

    override suspend fun answer(
        client: CoreClient,
        prompter: VpnAuthPrompter,
        endpointTag: String,
        endpointState: OpenConnectEndpointState,
    ) {
        val challenge = endpointState.authChallenge ?: return
        val browser = challenge.browser
        when {
            challenge.form != null -> submitForm(client, prompter, endpointTag, challenge)
            browser != null -> submitBrowser(client, prompter, endpointTag, challenge, browser)
            else -> throw VpnCliException("unsupported authentication challenge")
        }
    }

    override suspend fun cancel(client: CoreClient, endpointTag: String, challengeId: String) {
        client.cancelOpenConnectAuthChallenge(endpointTag, challengeId)
    }

    private suspend fun submitForm(
        client: CoreClient,
        prompter: VpnAuthPrompter,
        endpointTag: String,
        challenge: OpenConnectAuthChallengeState,
    ) {
        requireAuthTerminal(name)
        writeAuthHeader(endpointTag, "authentication")
        var preambleWritten = false
        if (challenge.banner.isNotEmpty()) {
            writeAuthBanner(challenge.banner)
            preambleWritten = true
        }
        if (challenge.error.isNotEmpty()) {
            printErrorLine("previous attempt failed: ${challenge.error}")
            preambleWritten = true
        }
        if (challenge.message.isNotEmpty()) {
            printErrorLine(challenge.message)
            preambleWritten = true
        }
        if (preambleWritten) printErrorLine("")
        val fields = challenge.form?.fields.orEmpty()
        while (true) {
            val values = mutableMapOf<String, String>()
            for (field in fields) {
                values[field.submissionKey] = promptField(prompter, field)
            }
            val accepted = submitVpnResponse(name) {
                client.submitOpenConnectAuthResponse(
                    openConnectAuthResponseSubmission {
                        this.endpointTag = endpointTag
                        challengeID = challenge.id
                        form = openConnectAuthFormResponse { this.values.putAll(values) }
                    },
                )
            }
            if (accepted) return
        }
    }

    private suspend fun promptField(
        prompter: VpnAuthPrompter,
        field: OpenConnectAuthField,
    ): String {
        val label = field.label.ifEmpty { field.name }
        return when (field.kind) {
            OPENCONNECT_FIELD_TEXT -> prompter.promptText(label, field.value)
            OPENCONNECT_FIELD_PASSWORD -> prompter.promptPassword(label, field.value)
            OPENCONNECT_FIELD_SELECT -> promptSelect(prompter, label, field.options, field.value)
            else -> throw VpnCliException("unsupported authentication field kind: ${field.kind}")
        }
    }

    private suspend fun promptSelect(
        prompter: VpnAuthPrompter,
        label: String,
        options: List<OpenConnectAuthChoice>,
        defaultValue: String,
    ): String {
        val prompt = label.removeSuffix(":")
        printErrorLine("$prompt:")
        for ((index, option) in options.withIndex()) {
            val optionLabel = option.label.ifEmpty { option.value }
            val defaultMark = if (option.value == defaultValue) "  [default]" else ""
            printErrorLine("  ${index + 1}) $optionLabel$defaultMark")
        }
        while (true) {
            val line = prompter.read("$prompt: ", hidden = false).trim()
            if (line.isEmpty() && defaultValue.isNotEmpty()) return defaultValue
            val selected = line.toIntOrNull()
            if (selected != null && selected in 1..options.size) return options[selected - 1].value
            if (options.any { it.value == line }) return line
            printErrorLine("select a number between 1 and ${options.size}")
        }
    }

    private suspend fun submitBrowser(
        client: CoreClient,
        prompter: VpnAuthPrompter,
        endpointTag: String,
        challenge: OpenConnectAuthChallengeState,
        request: OpenConnectBrowserRequestState,
    ) {
        writeAuthHeader(endpointTag, "browser authentication")
        if (challenge.error.isNotEmpty()) {
            printErrorLine("previous attempt failed: ${challenge.error}")
        }
        if (challenge.message.isNotEmpty()) printErrorLine(challenge.message)
        while (true) {
            val answer = collectBrowserAnswer(prompter, request)
            val accepted = submitVpnResponse(name, "browser authentication rejected") {
                client.submitOpenConnectAuthResponse(
                    openConnectAuthResponseSubmission {
                        this.endpointTag = endpointTag
                        challengeID = challenge.id
                        browser = answer.result
                    },
                )
            }
            if (accepted) {
                if (answer.reportsFailure) {
                    printErrorLine("single sign-on failed; the client will retry authentication")
                }
                return
            }
        }
    }

    /**
     * @property reportsFailure whether the answer carries the server's error cookie rather than a successful sign-on.
     */
    private class BrowserAnswer(
        val result: OpenConnectBrowserResult,
        val reportsFailure: Boolean,
    )

    private suspend fun collectBrowserAnswer(
        prompter: VpnAuthPrompter,
        request: OpenConnectBrowserRequestState,
    ): BrowserAnswer = when (request.completionMode) {
        OpenConnectBrowserCompletionMode.Callback -> {
            requireAuthTerminal(name)
            val target = parseCallbackTarget(request.callbackUrlPrefixes, callbackPort)
            val capturedUrl = runCallbackListener(prompter, target, request.url)
            BrowserAnswer(openConnectBrowserResult { finalURL = capturedUrl }, reportsFailure = false)
        }

        OpenConnectBrowserCompletionMode.Cookie -> {
            requireAuthTerminal(name)
            promptBrowserCookies(prompter, request)
        }

        OpenConnectBrowserCompletionMode.Header -> throw VpnCliException(
            "this single sign-on requires reading HTTP response headers, which cannot be done from a terminal; use the $APP_NAME app",
        )

        OpenConnectBrowserCompletionMode.Invalid -> throw VpnCliException(
            "openconnect browser request must select exactly one completion mode",
        )
    }

    private suspend fun promptBrowserCookies(
        prompter: VpnAuthPrompter,
        request: OpenConnectBrowserRequestState,
    ): BrowserAnswer {
        printErrorLine("This single sign-on must be completed manually.")
        printErrorLine("")
        var step = 1
        printErrorLine(" $step. Open this URL in any browser:")
        printErrorLine("      ${request.url}")
        if (request.finalUrl.isNotEmpty()) {
            step++
            printErrorLine(" $step. Sign in until the browser lands on:")
            printErrorLine("      ${request.finalUrl}")
        }
        step++
        printErrorLine(" $step. Open the developer tools (F12) > Application > Cookies, and read the")
        printErrorLine("    value of the cookie listed below for that page.")
        printErrorLine("")
        val errorCookieName = request.earlyCookieNames.firstOrNull()
        val cookies = mutableMapOf<String, String>()
        for ((index, name) in request.cookieNames.withIndex()) {
            val offersError = index == 0 && errorCookieName != null
            val prompt = if (offersError) {
                "Cookie \"$name\" (or \"!\" if the page reported an error): "
            } else {
                "Cookie \"$name\": "
            }
            while (true) {
                val value = prompter.read(prompt, hidden = true)
                if (offersError && value == "!") {
                    val errorValue = promptRequiredCookie(prompter, "Error cookie \"$errorCookieName\": ")
                    return BrowserAnswer(
                        openConnectBrowserResult {
                            this.cookies += openConnectBrowserCookie {
                                this.name = errorCookieName
                                this.value = errorValue
                            }
                        },
                        reportsFailure = true,
                    )
                }
                if (value.isEmpty()) {
                    printErrorLine("cookie value must not be empty")
                    continue
                }
                cookies[name] = value
                break
            }
        }
        printErrorLine(if (cookies.size == 1) "submitting 1 cookie" else "submitting ${cookies.size} cookies")
        return BrowserAnswer(
            openConnectBrowserResult {
                finalURL = request.finalUrl
                for ((name, value) in cookies) {
                    this.cookies += openConnectBrowserCookie {
                        this.name = name
                        this.value = value
                    }
                }
            },
            reportsFailure = false,
        )
    }

    private suspend fun promptRequiredCookie(prompter: VpnAuthPrompter, prompt: String): String {
        while (true) {
            val value = prompter.read(prompt, hidden = true)
            if (value.isNotEmpty()) return value
            printErrorLine("cookie value must not be empty")
        }
    }
}

private fun challengeSummary(challenge: OpenConnectAuthChallengeState): String {
    val form = challenge.form
    val browser = challenge.browser
    return when {
        form != null -> "form (${form.fields.size} fields)"
        browser != null -> "browser (${browser.completionMode.name.lowercase()})"
        else -> "unknown"
    }
}

private fun openConnectPhase(state: String) = when (state) {
    OPENCONNECT_STATE_CONNECTED -> VpnEndpointPhase.Connected
    OPENCONNECT_STATE_AUTH_PENDING -> VpnEndpointPhase.AuthPending
    OPENCONNECT_STATE_ERROR -> VpnEndpointPhase.Error
    else -> VpnEndpointPhase.Connecting
}

private class CallbackTarget(val scheme: String, val host: String, val port: Int) {
    fun resolve(requestUri: String): String = "$scheme://${host.wrapIPV6Host()}:$port$requestUri"
}

private fun parseCallbackTarget(prefixes: List<String>, defaultPort: Int): CallbackTarget {
    var scheme = ""
    var host = ""
    var port = -1
    for (prefix in prefixes) {
        val uri = runCatching { URI(prefix) }.getOrNull()
        val prefixHost = uri?.host.orEmpty()
        if (!prefixHost.isLoopbackHost()) {
            throw VpnCliException("callback URL prefix is not on loopback: $prefix")
        }
        if (scheme.isEmpty()) {
            scheme = uri?.scheme.orEmpty()
            host = prefixHost.unwrapIPV6Host()
        }
        if (port == -1 && uri != null && uri.port != -1) {
            port = uri.port
        }
    }
    return CallbackTarget(scheme, host, if (port == -1) defaultPort else port)
}

/** Serves the single sign-on callback until the browser hits it, or the challenge is withdrawn. */
private suspend fun runCallbackListener(
    prompter: VpnAuthPrompter,
    target: CallbackTarget,
    loginUrl: String,
): String {
    val server = try {
        HttpServer.create(InetSocketAddress(LOOPBACK_LISTEN_ADDRESS, target.port), 0)
    } catch (e: IOException) {
        throw VpnCliException(
            "cannot listen on $LOOPBACK_LISTEN_ADDRESS:${target.port}: ${e.readableMessage}; pass --callback-port",
        )
    }
    val callbackUri = CompletableDeferred<String>()
    server.createContext("/") { exchange ->
        callbackUri.complete(exchange.requestURI.toString())
        val body = CALLBACK_PAGE.toByteArray()
        exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }
    server.executor = null
    server.start()
    try {
        printErrorLine("Complete single sign-on in your browser; this command finishes automatically.")
        printErrorLine("")
        printErrorLine("  listening on  ${target.resolve("/")}")
        printErrorLine("  url           $loginUrl")
        printErrorLine("")
        if (!prompter.promptConfirm("Open it now? [Y/n] ")) {
            printErrorLine("waiting for the callback...")
        } else {
            val openError = openUri(loginUrl)
            if (openError != null) {
                printErrorLine("failed to open the default browser: $openError")
                printErrorLine("waiting for the callback...")
            } else {
                printErrorLine("opened in the default browser; waiting for the callback...")
            }
        }
        return select {
            callbackUri.onAwait { requestUri ->
                printErrorLine("received callback")
                target.resolve(requestUri)
            }
            prompter.aborted.onAwait { throw it }
        }
    } finally {
        server.stop(0)
    }
}
