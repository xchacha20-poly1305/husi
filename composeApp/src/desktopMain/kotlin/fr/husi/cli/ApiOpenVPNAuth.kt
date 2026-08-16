package fr.husi.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import fr.husi.core.CoreClient
import fr.husi.ktx.openUri
import fr.husi.proto.daemon.openVPNChallengeSubmission
import fr.husi.vpn.OPENVPN_CHALLENGE_CREDENTIALS
import fr.husi.vpn.OPENVPN_CHALLENGE_MESSAGE
import fr.husi.vpn.OPENVPN_CHALLENGE_OPEN_URL
import fr.husi.vpn.OPENVPN_CHALLENGE_SECRET
import fr.husi.vpn.OPENVPN_STATE_AUTH_PENDING
import fr.husi.vpn.OPENVPN_STATE_CONNECTED
import fr.husi.vpn.OPENVPN_STATE_ERROR
import fr.husi.vpn.OpenVPNChallengeState
import fr.husi.vpn.OpenVPNEndpointState
import fr.husi.vpn.toState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val OPENVPN_NAME = "openvpn"

internal class ApiOpenVPNCommand : CliktCommand(OPENVPN_NAME) {
    init {
        subcommands(
            ApiVpnStatusCommand(OpenVPNAuthProtocol),
            ApiOpenVPNAuthCommand(),
            ApiVpnCancelCommand(OpenVPNAuthProtocol),
        )
    }

    override fun help(context: Context) = "Manage OpenVPN authentication"

    override fun run() = Unit
}

private class ApiOpenVPNAuthCommand : ApiVpnAuthCommand<OpenVPNEndpointState>(OPENVPN_NAME) {
    override fun help(context: Context) =
        "Answer OpenVPN authentication challenges.\n\nCtrl-C leaves the challenge pending: run the command again to resume, or use `cancel` to stop the client."

    override fun protocol() = OpenVPNAuthProtocol
}

internal object OpenVPNAuthProtocol : VpnAuthProtocol<OpenVPNEndpointState> {
    override val name = OPENVPN_NAME

    // sing-openvpn treats a canceled challenge as terminal: unlike OpenConnect, the client does
    // not reconnect afterwards.
    override val canceledMessage = "authentication challenge canceled; the client has stopped"

    override fun subscribe(client: CoreClient): Flow<List<VpnEndpointView<OpenVPNEndpointState>>> =
        client.subscribeOpenVPNStatus().map { update ->
            update.endpointsList.map { status ->
                val endpointState = status.toState()
                VpnEndpointView(
                    tag = endpointState.tag,
                    phase = openVPNPhase(endpointState.state),
                    state = endpointState.state,
                    error = endpointState.error,
                    challengeId = endpointState.challenge?.id,
                    endpointState = endpointState,
                )
            }
        }

    override fun challengeDeadline(endpointState: OpenVPNEndpointState) = endpointState.challenge?.deadline ?: 0L

    override fun describe(block: BlockWriter, endpoint: VpnEndpointView<OpenVPNEndpointState>) {
        val challenge = endpoint.endpointState.challenge
        val tunnelInfo = endpoint.endpointState.tunnelInfo
        when {
            challenge != null -> {
                block.addLine("Challenge", challengeSummary(challenge))
                if (challenge.message.isNotEmpty()) block.addLine("Message", challenge.message)
                if (challenge.url.isNotEmpty()) block.addLine("URL", challenge.url)
                if (challenge.deadline != 0L) {
                    block.addLine("Deadline", "in ${formatGoDuration(vpnRemaining(challenge.deadline))}")
                }
                if (challenge.previousError.isNotEmpty()) {
                    block.addLine("Error", challenge.previousError)
                }
            }

            tunnelInfo != null -> {
                block.addLine("Server", tunnelInfo.server)
                block.addLine("Network", tunnelInfo.network)
                block.addLine("Cipher", tunnelInfo.cipher)
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
        endpointState: OpenVPNEndpointState,
    ) {
        val challenge = endpointState.challenge ?: return
        when (challenge.kind) {
            OPENVPN_CHALLENGE_CREDENTIALS ->
                submitCredentials(client, prompter, endpointTag, challenge)

            OPENVPN_CHALLENGE_SECRET -> submitSecret(client, prompter, endpointTag, challenge)

            OPENVPN_CHALLENGE_MESSAGE -> {
                writeAuthHeader(endpointTag, "notice")
                printErrorLine(challenge.message + formatVpnRemainingSuffix(challenge.deadline))
            }

            OPENVPN_CHALLENGE_OPEN_URL -> openChallengeUrl(prompter, endpointTag, challenge)

            else -> throw VpnCliException(
                "unsupported authentication challenge kind: ${challenge.kind}",
            )
        }
    }

    override suspend fun cancel(client: CoreClient, endpointTag: String, challengeId: String) {
        client.cancelOpenVPNChallenge(endpointTag, challengeId)
    }

    private suspend fun submitCredentials(
        client: CoreClient,
        prompter: VpnAuthPrompter,
        endpointTag: String,
        challenge: OpenVPNChallengeState,
    ) {
        requireAuthTerminal(name)
        writeAuthHeader(endpointTag, "authentication")
        if (challenge.previousError.isNotEmpty()) {
            printErrorLine("previous attempt failed: ${challenge.previousError}")
            printErrorLine("")
        }
        val secretLabel = challenge.secretMessage.ifEmpty { "Secret" }.removeSuffix(":")
        while (true) {
            val username = prompter.promptText("Username", challenge.username)
            val password = prompter.promptPassword("Password", "")
            val secret = prompter.read("$secretLabel: ", hidden = !challenge.echo)
            val accepted = submitVpnResponse(name) {
                client.submitOpenVPNChallengeResponse(
                    openVPNChallengeSubmission {
                        this.endpointTag = endpointTag
                        challengeID = challenge.id
                        this.username = username
                        this.password = password
                        this.secret = secret
                    },
                )
            }
            if (accepted) return
        }
    }

    private suspend fun submitSecret(
        client: CoreClient,
        prompter: VpnAuthPrompter,
        endpointTag: String,
        challenge: OpenVPNChallengeState,
    ) {
        requireAuthTerminal(name)
        writeAuthHeader(endpointTag, "authentication")
        var contextWritten = false
        if (challenge.previousError.isNotEmpty()) {
            printErrorLine("previous attempt failed: ${challenge.previousError}")
            contextWritten = true
        }
        if (challenge.username.isNotEmpty()) {
            printErrorLine("user: ${challenge.username}")
            contextWritten = true
        }
        if (contextWritten) printErrorLine("")
        // A challenge with a deadline is a one time code: its message describes how to obtain
        // the code rather than what to type, so it is printed and the prompt stays generic.
        val label = if (challenge.deadline == 0L) {
            challenge.message.ifEmpty { "Secret" }
        } else {
            if (challenge.message.isNotEmpty()) {
                printErrorLine(challenge.message + formatVpnRemainingSuffix(challenge.deadline))
            }
            "Code"
        }.removeSuffix(":")
        while (true) {
            val secret = prompter.read("$label: ", hidden = !challenge.echo)
            val accepted = submitVpnResponse(name) {
                client.submitOpenVPNChallengeResponse(
                    openVPNChallengeSubmission {
                        this.endpointTag = endpointTag
                        challengeID = challenge.id
                        this.secret = secret
                    },
                )
            }
            if (accepted) return
        }
    }

    private suspend fun openChallengeUrl(
        prompter: VpnAuthPrompter,
        endpointTag: String,
        challenge: OpenVPNChallengeState,
    ) {
        writeAuthHeader(endpointTag, "authentication")
        if (challenge.previousError.isNotEmpty()) {
            printErrorLine("previous attempt failed: ${challenge.previousError}")
        }
        printErrorLine("Complete authentication in your browser:")
        printErrorLine("")
        printErrorLine("  ${challenge.url}")
        printErrorLine("")
        val waitingMessage = "waiting for the server" + formatVpnRemainingSuffix(challenge.deadline)
        if (isTerminal && prompter.promptConfirm("Open it now? [Y/n] ")) {
            val openError = openUri(challenge.url)
            if (openError == null) {
                printErrorLine("opened in the default browser; $waitingMessage")
                return
            }
            printErrorLine("failed to open the default browser: $openError")
        }
        printErrorLine(waitingMessage)
    }

    private fun challengeSummary(challenge: OpenVPNChallengeState): String =
        if (challenge.answerable) challenge.kind else "${challenge.kind} (not answerable)"
}

private fun openVPNPhase(state: String) = when (state) {
    OPENVPN_STATE_CONNECTED -> VpnEndpointPhase.Connected
    OPENVPN_STATE_AUTH_PENDING -> VpnEndpointPhase.AuthPending
    OPENVPN_STATE_ERROR -> VpnEndpointPhase.Error
    else -> VpnEndpointPhase.Connecting
}
