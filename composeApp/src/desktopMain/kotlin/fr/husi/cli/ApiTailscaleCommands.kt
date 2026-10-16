package fr.husi.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.google.protobuf.ByteString
import fr.husi.CLI_STREAM_TIMEOUT
import fr.husi.core.CoreClient
import fr.husi.core.TAILDROP_CHUNK_SIZE
import fr.husi.ktx.isIpAddress
import fr.husi.ktx.parseGoDuration
import fr.husi.platform.PlatformInfo
import fr.husi.repository.resolveOnPath
import fr.husi.proto.daemon.TaildropOutgoingFile
import fr.husi.proto.daemon.TaildropSendClientMessage
import fr.husi.proto.daemon.TailscaleEndpointStatus
import fr.husi.proto.daemon.TailscalePeer
import fr.husi.proto.daemon.TailscalePingResponse
import fr.husi.proto.daemon.TailscaleUserGroup
import fr.husi.proto.daemon.taildropFileChunk
import fr.husi.proto.daemon.taildropFileDone
import fr.husi.proto.daemon.taildropOutgoingFile
import fr.husi.proto.daemon.taildropSendClientMessage
import fr.husi.proto.daemon.taildropSendStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.OutputStream
import java.nio.file.attribute.PosixFilePermissions
import java.net.InetAddress
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.writeBytes
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

internal interface TailscaleEndpointFlagged {
    val endpointOption: String
}

internal abstract class ApiTailscaleCommandBase(name: String) : ApiClientCommand(name), TailscaleEndpointFlagged {
    val endpoint by option(
        "--endpoint",
        help = "Tailscale endpoint tag (default: the only Tailscale endpoint)",
    ).default("")

    override val endpointOption: String
        get() = endpoint

    protected suspend fun resolveEndpointTag(client: CoreClient): String {
        val flagValue = findEndpointOption()
        if (flagValue.isNotEmpty()) {
            return flagValue
        }
        val endpointStatus = fetchTailscaleEndpoint(client)
        return endpointStatus.endpointTag
    }

    protected suspend fun fetchTailscaleEndpoint(client: CoreClient): TailscaleEndpointStatus {
        val endpoints = fetchTailscaleStatus(client)
        val flagValue = findEndpointOption()
        return resolveTailscaleEndpointStatus(endpoints, flagValue)
    }

    protected fun resolvePeer(
        entries: List<TailscalePeerEntry>,
        selector: String,
    ): TailscalePeerEntry {
        val result = resolveTailscalePeer(entries, selector)
        if (result.error != null) {
            echo(result.error, err = true)
            throw ProgramResult(1)
        }
        return result.entry!!
    }

    private fun findEndpointOption(): String {
        if (endpoint.isNotEmpty()) return endpoint
        return generateSequence(currentContext.parent) { it.parent }
            .mapNotNull { it.command as? TailscaleEndpointFlagged }
            .firstOrNull { it.endpointOption.isNotEmpty() }
            ?.endpointOption.orEmpty()
    }
}

internal class ApiTailscaleCommand : CliktCommand("tailscale") {
    init {
        subcommands(
            ApiTailscaleStatusCommand(),
            ApiTailscalePeerCommand(),
            ApiTailscaleExitNodeCommand(),
            ApiTailscalePingCommand(),
            ApiTailscaleLogoutCommand(),
            ApiTailscaleCertificateCommand(),
            ApiTailscaleTaildropCommand(),
            ApiTailscaleSshCommand(),
        )
    }

    override fun help(context: Context) = "Manage Tailscale endpoints"

    override fun run() = Unit
}

private class ApiTailscaleStatusCommand : ApiClientCommand("status") {
    override fun help(context: Context) = "Print the status of Tailscale endpoints"

    override fun run() = withClient { client ->
        val endpoints = fetchTailscaleStatus(client)
        if (endpoints.isEmpty()) {
            echo("no tailscale endpoint found", err = true)
            throw ProgramResult(1)
        }
        for ((index, endpoint) in endpoints.withIndex()) {
            if (index > 0) {
                println()
            }
            println(endpoint.endpointTag)
            val block = BlockWriter()
            block.addLine("  State", endpoint.backendState)
            if (endpoint.networkName.isNotEmpty()) {
                block.addLine("  Network", endpoint.networkName)
            }
            if (endpoint.keyAuth) {
                block.addLine("  Auth", "auth key")
            }
            if (endpoint.authURL.isNotEmpty()) {
                block.addLine("  Log in", endpoint.authURL)
            }
            block.flush()
        }
    }
}

internal class ApiTailscalePeerCommand : CliktCommand("peer"), TailscaleEndpointFlagged {
    val endpoint by option(
        "--endpoint",
        help = "Tailscale endpoint tag (default: the only Tailscale endpoint)",
    ).default("")

    override val endpointOption: String
        get() = endpoint

    init {
        subcommands(
            ApiTailscalePeerListCommand(),
            ApiTailscalePeerShowCommand(),
        )
    }

    override fun help(context: Context) = "Manage Tailscale peers"

    override fun run() = Unit
}

private class ApiTailscalePeerListCommand : ApiTailscaleCommandBase("list") {
    override fun help(context: Context) = "List Tailscale peers"

    override fun run() = withClient { client ->
        val endpoint = fetchTailscaleEndpoint(client)
        val entries = tailscalePeerEntries(endpoint)
        val resultEntries = if (entries.isNotEmpty() && entries[0].self) {
            listOf(entries[0]) + entries.drop(1).sortedBy { tailscalePeerName(it.peer).lowercase() }
        } else {
            entries.sortedBy { tailscalePeerName(it.peer).lowercase() }
        }
        val table = TableWriter(
            header = listOf("DNS NAME", "IP", "ONLINE"),
            emptyMessage = "no peers",
        )
        for (entry in resultEntries) {
            table.addRow(
                tailscalePeerName(entry.peer),
                tailscalePeerAddress(entry.peer),
                formatYesNo(entry.peer.online),
            )
        }
        table.flush()
    }
}

private class ApiTailscalePeerShowCommand : ApiTailscaleCommandBase("show") {
    private val peerSelector by argument(name = "peer", help = "Tailscale peer")

    override fun help(context: Context) = "Print Tailscale peer details"

    override fun run() = withClient { client ->
        val endpoint = fetchTailscaleEndpoint(client)
        val entry = resolvePeer(tailscalePeerEntries(endpoint), peerSelector)
        val peer = entry.peer
        val exitNode = when {
            peer.exitNode -> "in use"
            peer.exitNodeOption -> "offered"
            else -> "no"
        }
        val block = BlockWriter()
        block.addLine("DNS name", peer.dnsName.removeSuffix("."))
        block.addLine("Host name", peer.hostName)
        block.addLine("Stable ID", peer.stableID)
        block.addLine("User", formatTailscaleUser(entry.group))
        block.addLine("OS", peer.os)
        block.addLine("IPs", peer.tailscaleIPsList.joinToString(", "))
        block.addLine("Online", formatYesNo(peer.online))
        block.addLine("Active", formatYesNo(peer.active))
        block.addLine("Expired", formatYesNo(peer.expired))
        block.addLine("Sharee node", formatYesNo(peer.shareeNode))
        block.addLine("Exit node", exitNode)
        block.addLine("Rx", peer.rxBytes.toString())
        block.addLine("Tx", peer.txBytes.toString())
        block.addLine("Key expiry", formatTailscaleTime(peer.keyExpiry))
        block.addLine("Last seen", formatTailscaleTime(peer.lastSeen))
        block.addLine("SSH host keys", formatTailscaleSSHHostKeys(peer.sshHostKeysList))
        block.flush()
    }
}

internal class ApiTailscaleExitNodeCommand : ApiTailscaleCommandBase("exit-node") {
    override val invokeWithoutSubcommand = true

    init {
        subcommands(
            ApiTailscaleExitNodeListCommand(),
            ApiTailscaleExitNodeSetCommand(),
            ApiTailscaleExitNodeClearCommand(),
        )
    }

    override fun help(context: Context) = "Print the current Tailscale exit node"

    override fun run() {
        if (currentContext.invokedSubcommand != null) return
        withClient { client ->
            val endpoint = fetchTailscaleEndpoint(client)
            if (!endpoint.hasExitNode()) {
                println("none")
                return@withClient
            }
            val exitNode = endpoint.exitNode
            val name = tailscalePeerName(exitNode)
            val address = tailscalePeerAddress(exitNode)
            if (address.isEmpty()) {
                println(name)
            } else {
                println("$name ($address)")
            }
        }
    }
}

private class ApiTailscaleExitNodeListCommand : ApiTailscaleCommandBase("list") {
    override fun help(context: Context) = "List available Tailscale exit nodes"

    override fun run() = withClient { client ->
        val endpoint = fetchTailscaleEndpoint(client)
        val selectedStableId = if (endpoint.hasExitNode()) endpoint.exitNode.stableID else ""
        val candidates = tailscalePeerEntries(endpoint)
            .filter { !it.self && it.peer.exitNodeOption }
            .sortedBy { tailscalePeerName(it.peer).lowercase() }

        val table = TableWriter(
            header = listOf("DNS NAME", "IP", "ONLINE", "STATUS"),
            emptyMessage = "no exit nodes",
        )
        for (entry in candidates) {
            val status = if (selectedStableId.isNotEmpty() && entry.peer.stableID == selectedStableId) {
                "selected"
            } else {
                ""
            }
            table.addRow(
                tailscalePeerName(entry.peer),
                tailscalePeerAddress(entry.peer),
                formatYesNo(entry.peer.online),
                status,
            )
        }
        table.flush()
    }
}

private class ApiTailscaleExitNodeSetCommand : ApiTailscaleCommandBase("set") {
    private val peerSelector by argument(name = "peer", help = "Tailscale peer")

    override fun help(context: Context) = "Use a Tailscale peer as exit node"

    override fun run() = withClient { client ->
        val endpoint = fetchTailscaleEndpoint(client)
        val entry = resolvePeer(tailscalePeerEntries(endpoint), peerSelector)
        client.setTailscaleExitNode(endpoint.endpointTag, entry.peer.stableID)
    }
}

private class ApiTailscaleExitNodeClearCommand : ApiTailscaleCommandBase("clear") {
    override fun help(context: Context) = "Stop using a Tailscale exit node"

    override fun run() = withClient { client ->
        val endpointTag = resolveEndpointTag(client)
        client.setTailscaleExitNode(endpointTag, "")
    }
}

private class ApiTailscalePingCommand : ApiTailscaleCommandBase("ping") {
    private val peerSelector by argument(name = "peer", help = "Tailscale peer")

    override fun help(context: Context) = "Ping a Tailscale peer"

    override fun run() = withClient { client ->
        val endpoint = fetchTailscaleEndpoint(client)
        val entry = resolvePeer(tailscalePeerEntries(endpoint), peerSelector)
        val peerAddress = tailscalePeerAddress(entry.peer)
        if (peerAddress.isEmpty()) {
            echo("peer has no tailscale address: ${tailscalePeerName(entry.peer)}", err = true)
            throw ProgramResult(1)
        }
        val peerName = tailscalePeerName(entry.peer).substringBefore('.')
        coroutineScope {
            val channel = Channel<TailscalePingResponse>(Channel.UNLIMITED)
            val job = launch {
                try {
                    client.tailscalePing(endpoint.endpointTag, peerAddress).collect { channel.send(it) }
                } catch (e: Throwable) {
                    channel.close(e)
                } finally {
                    channel.close()
                }
            }
            try {
                var pongCount = 0
                var lastPeerRelay = ""
                while (pongCount < 10) {
                    val result = withTimeoutOrNull(5.seconds) {
                        channel.receiveCatching()
                    } ?: run {
                        echo("no reply from $peerName ($peerAddress) after 5s", err = true)
                        throw ProgramResult(1)
                    }
                    if (result.isClosed) {
                        result.exceptionOrNull()?.let { throw it }
                        break
                    }
                    val response = result.getOrThrow()
                    if (response.error.isNotEmpty()) {
                        echo("ping error: ${response.error}", err = true)
                        throw ProgramResult(1)
                    }
                    println(formatTailscalePong(peerName, peerAddress, response))
                    pongCount++
                    if (response.endpoint.isNotEmpty()) {
                        return@coroutineScope
                    }
                    lastPeerRelay = response.peerRelay
                }
                if (lastPeerRelay.isNotEmpty()) {
                    println("direct connection not established, relayed by peer relay $lastPeerRelay")
                } else {
                    println("direct connection not established")
                }
            } finally {
                job.cancel()
            }
        }
    }
}

private class ApiTailscaleLogoutCommand : ApiTailscaleCommandBase("logout") {
    override fun help(context: Context) = "Log out of the Tailscale network"

    override fun run() = withClient { client ->
        val endpointTag = resolveEndpointTag(client)
        client.tailscaleLogout(endpointTag)
    }
}

internal class ApiTailscaleCertificateCommand : CliktCommand("certificate"), TailscaleEndpointFlagged {
    val endpoint by option(
        "--endpoint",
        help = "Tailscale endpoint tag (default: the only Tailscale endpoint)",
    ).default("")

    override val endpointOption: String
        get() = endpoint

    init {
        subcommands(
            ApiTailscaleCertificateListCommand(),
            ApiTailscaleCertificateExportCommand(),
        )
    }

    override fun help(context: Context) = "Manage Tailscale HTTPS certificates"

    override fun run() = Unit
}

private class ApiTailscaleCertificateListCommand : ApiTailscaleCommandBase("list") {
    override fun help(context: Context) = "List domains that certificates can be issued for"

    override fun run() = withClient { client ->
        val endpoint = fetchTailscaleEndpoint(client)
        val table = TableWriter(
            header = listOf("DOMAIN"),
            emptyMessage = "no certificate domains, enable HTTPS in the Tailscale admin console",
        )
        for (domain in endpoint.certDomainsList) {
            table.addRow(domain)
        }
        table.flush()
    }
}

private class ApiTailscaleCertificateExportCommand : ApiTailscaleCommandBase("export") {

    private companion object {
        const val CERTIFICATE_PERMISSIONS = "rw-r--r--"
        const val CERTIFICATE_KEY_PERMISSIONS = "rw-------"
    }

    private val domain by argument(name = "domain", help = "Certificate domain")

    private val certFile by option(
        "--cert-file",
        help = "Output certificate file (default: <domain>.crt, \"-\" for stdout)",
    ).default("")

    private val keyFile by option(
        "--key-file",
        help = "Output private key file (default: <domain>.key, \"-\" for stdout)",
    ).default("")

    private val minValidity by option(
        "--min-validity",
        help = "Renew the certificate if it expires within this duration",
    ).default("")

    override fun help(context: Context) = "Export the HTTPS certificate and private key for a domain"

    override fun run() = withClient { client ->
        val endpoint = fetchTailscaleEndpoint(client)
        val certDomains = endpoint.certDomainsList
        if (certDomains.isEmpty()) {
            echo("no certificate domains, enable HTTPS in the Tailscale admin console", err = true)
            throw ProgramResult(1)
        }
        if (domain !in certDomains) {
            echo(
                "unknown certificate domain: $domain\nknown domains:\n${certDomains.joinToString("\n") { "  $it" }}",
                err = true,
            )
            throw ProgramResult(1)
        }
        val certificatePath = certFile.ifEmpty { "$domain.crt" }
        val privateKeyPath = keyFile.ifEmpty { "$domain.key" }
        val minValiditySeconds = parseMinValidity(minValidity)
        writeStderrLine("fetching certificate for $domain")
        val certificate = client.getTailscaleCertificate(endpoint.endpointTag, domain, minValiditySeconds)
        if (certificatePath == "-" && privateKeyPath == "-") {
            System.out.write(certificate.certificatePEM.toByteArray())
            System.out.write(certificate.privateKeyPEM.toByteArray())
            System.out.flush()
            return@withClient
        }
        writeTailscaleCertificateFile(certificatePath, certificate.certificatePEM.toByteArray(), isKey = false)
        writeTailscaleCertificateFile(privateKeyPath, certificate.privateKeyPEM.toByteArray(), isKey = true)
    }

    private fun parseMinValidity(value: String): Long {
        if (value.isEmpty()) return 0L
        return try {
            parseGoDuration(value).inWholeSeconds
        } catch (e: IllegalArgumentException) {
            echo(e.message, err = true)
            throw ProgramResult(1)
        }
    }

    private fun writeTailscaleCertificateFile(path: String, content: ByteArray, isKey: Boolean) {
        if (path == "-") {
            System.out.write(content)
            System.out.flush()
            return
        }
        val target = Path(path)
        if (!target.exists()) {
            createCertificateFile(target, isKey)
        }
        target.writeBytes(content)
        writeStderrLine("wrote $path")
    }

    // Create the file with its final permissions, so a private key is never readable by others.
    private fun createCertificateFile(target: Path, isKey: Boolean) {
        if (target.fileSystem.supportedFileAttributeViews().contains("posix")) {
            val permissions = if (isKey) {
                CERTIFICATE_KEY_PERMISSIONS
            } else {
                CERTIFICATE_PERMISSIONS
            }
            target.createFile(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(permissions)))
        }
    }
}

internal class ApiTailscaleTaildropCommand : CliktCommand("taildrop"), TailscaleEndpointFlagged {
    val endpoint by option(
        "--endpoint",
        help = "Tailscale endpoint tag (default: the only Tailscale endpoint)",
    ).default("")

    override val endpointOption: String
        get() = endpoint

    init {
        subcommands(
            ApiTailscaleTaildropTargetsCommand(),
            ApiTailscaleTaildropListCommand(),
            ApiTailscaleTaildropGetCommand(),
            ApiTailscaleTaildropSendCommand(),
            ApiTailscaleTaildropDeleteCommand(),
        )
    }

    override fun help(context: Context) = "Send and receive files over Tailscale"

    override fun run() = Unit
}

private class ApiTailscaleTaildropTargetsCommand : ApiTailscaleCommandBase("targets") {
    override fun help(context: Context) = "List peers that can receive files"

    override fun run() = withClient { client ->
        val endpoint = fetchTailscaleEndpoint(client)
        if (!endpoint.canShareFiles) {
            echo("file sharing not enabled by Tailscale admin", err = true)
            throw ProgramResult(1)
        }
        val entries = tailscalePeerEntries(endpoint)
            .let { if (it.isNotEmpty() && it[0].self) it.drop(1) else it }
            .sortedBy { tailscalePeerName(it.peer).lowercase() }

        val table = TableWriter(
            header = listOf("DNS NAME", "IP", "ONLINE"),
            emptyMessage = "no file targets",
        )
        for (entry in entries) {
            if (!entry.peer.canReceiveFiles) continue
            table.addRow(
                tailscalePeerName(entry.peer),
                tailscalePeerAddress(entry.peer),
                formatYesNo(entry.peer.online),
            )
        }
        table.flush()
    }
}

private class ApiTailscaleTaildropListCommand : ApiTailscaleCommandBase("list") {
    override fun help(context: Context) = "List received files"

    override fun run() = withClient { client ->
        val endpointTag = resolveEndpointTag(client)
        val inbox = withTimeout(CLI_STREAM_TIMEOUT) {
            client.subscribeTaildropInbox(endpointTag).first()
        }
        for (file in inbox.receivingList) {
            writeStderrLine("receiving ${file.name}: ${formatTaildropSize(file.receivedBytes)} / ${formatTaildropSize(file.size)}")
        }
        val table = TableWriter(
            header = listOf("NAME", "SIZE", "FROM", "RECEIVED"),
            emptyMessage = "no received files",
        )
        for (file in inbox.filesList) {
            val sender = file.senderName.ifEmpty { "-" }
            table.addRow(
                file.name,
                formatTaildropSize(file.size),
                sender,
                formatTaildropModifiedAt(file.modifiedAt),
            )
        }
        table.flush()
    }
}

private class ApiTailscaleTaildropGetCommand : ApiTailscaleCommandBase("get") {
    private val name by argument(name = "name", help = "File name")
    private val output by argument(name = "output", help = "Output path").optional()

    override fun help(context: Context) = "Save a received file"

    override fun run() = withClient { client ->
        val endpointTag = resolveEndpointTag(client)
        val outputPath = if (output.isNullOrEmpty()) {
            name
        } else {
            val target = File(output!!)
            if (target.exists() && target.isDirectory) {
                File(target, name).path
            } else {
                output!!
            }
        }
        val isStdout = outputPath == "-"
        var outputStream: OutputStream? = null
        var totalSize = 0L
        var downloaded = 0L

        // The output file is created on the first chunk, so a download the core rejects leaves nothing behind.
        try {
            client.downloadTaildropFile(endpointTag, name).collect { chunk ->
                val stream = outputStream ?: run {
                    totalSize = chunk.size
                    openTaildropOutput(outputPath, isStdout).also { outputStream = it }
                }
                val data = chunk.data.toByteArray()
                if (data.isNotEmpty()) {
                    stream.write(data)
                    downloaded += data.size
                    writeProgress("$name: ${formatTaildropSize(downloaded)} / ${formatTaildropSize(totalSize)}    ")
                }
            }
            if (!isStdout) {
                outputStream?.close()
            }
            writeProgress("")
            writeStderrLine("saved $name (${formatTaildropSize(downloaded)})")
        } catch (e: Exception) {
            if (!isStdout && outputStream != null) {
                runCatching { outputStream.close() }
                File(outputPath).delete()
            }
            throw e
        }
    }

    private fun openTaildropOutput(outputPath: String, isStdout: Boolean): OutputStream {
        if (isStdout) return System.out
        try {
            return Files.newOutputStream(
                File(outputPath).toPath(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            )
        } catch (e: FileAlreadyExistsException) {
            echo("open $outputPath: file exists", err = true)
            throw ProgramResult(1)
        }
    }
}

private class ApiTailscaleTaildropSendCommand : ApiTailscaleCommandBase("send") {
    private val peerSelector by argument(name = "peer", help = "Destination Tailscale peer")
    private val filePaths by argument(name = "file", help = "Files to send").multiple(required = true)

    override fun help(context: Context) = "Send files to a Tailscale peer"

    override fun run() = withClient { client ->
        val endpoint = fetchTailscaleEndpoint(client)
        if (!endpoint.canShareFiles) {
            echo("file sharing not enabled by Tailscale admin", err = true)
            throw ProgramResult(1)
        }
        val entry = resolvePeer(tailscalePeerEntries(endpoint), peerSelector)
        if (entry.self) {
            echo("cannot send files to this device", err = true)
            throw ProgramResult(1)
        }
        if (!entry.peer.canReceiveFiles) {
            echo("peer cannot receive files: ${tailscalePeerName(entry.peer)}", err = true)
            throw ProgramResult(1)
        }
        data class OutgoingFile(val file: File, val name: String, val size: Long)
        val outgoingFiles = mutableListOf<OutgoingFile>()
        val manifest = mutableListOf<TaildropOutgoingFile>()
        for (path in filePaths) {
            val file = File(path)
            if (!file.exists()) {
                echo("stat $path: no such file or directory", err = true)
                throw ProgramResult(1)
            }
            if (file.isDirectory) {
                echo("directories are not supported: $path", err = true)
                throw ProgramResult(1)
            }
            val size = file.length()
            val name = file.name
            outgoingFiles += OutgoingFile(file, name, size)
            manifest += taildropOutgoingFile {
                this.name = name
                this.size = size
            }
        }

        val requestChannel = Channel<TaildropSendClientMessage>(Channel.BUFFERED)
        coroutineScope {
            val uploadJob = launch(Dispatchers.IO) {
                try {
                    requestChannel.send(
                        taildropSendClientMessage {
                            start = taildropSendStart {
                                endpointTag = endpoint.endpointTag
                                peerStableID = entry.peer.stableID
                                files.addAll(manifest)
                            }
                        },
                    )
                    val buffer = ByteArray(TAILDROP_CHUNK_SIZE)
                    for (item in outgoingFiles) {
                        item.file.inputStream().use { stream ->
                            while (true) {
                                val n = stream.read(buffer)
                                if (n > 0) {
                                    requestChannel.send(
                                        taildropSendClientMessage {
                                            chunk = taildropFileChunk {
                                                data = ByteString.copyFrom(buffer, 0, n)
                                            }
                                        },
                                    )
                                }
                                if (n == -1) break
                            }
                        }
                        requestChannel.send(
                            taildropSendClientMessage {
                                fileDone = taildropFileDone { }
                            },
                        )
                    }
                } finally {
                    requestChannel.close()
                }
            }

            val peerName = tailscalePeerName(entry.peer)
            try {
                client.sendTaildropFiles(requestChannel.consumeAsFlow()).collect { serverMessage ->
                    if (!serverMessage.hasProgress()) return@collect
                    val progress = serverMessage.progress
                    val fileIndex = progress.fileIndex
                    if (fileIndex < 0 || fileIndex >= outgoingFiles.size) {
                        echo("invalid file index: $fileIndex", err = true)
                        throw ProgramResult(1)
                    }
                    val file = outgoingFiles[fileIndex]
                    if (progress.fileCompleted) {
                        writeProgress("")
                        writeStderrLine("${file.name}: done")
                    } else {
                        writeProgress("${file.name}: ${formatTaildropSize(progress.sentBytes)} / ${formatTaildropSize(file.size)}    ")
                    }
                }
                uploadJob.join()
                writeProgress("")
                writeStderrLine("sent ${outgoingFiles.size} file(s) to $peerName")
            } catch (e: Exception) {
                uploadJob.cancel()
                throw e
            }
        }
    }
}

private class ApiTailscaleTaildropDeleteCommand : ApiTailscaleCommandBase("delete") {
    private val names by argument(name = "name", help = "Files to delete").multiple(required = true)

    override fun help(context: Context) = "Delete received files"

    override fun run() = withClient { client ->
        val endpointTag = resolveEndpointTag(client)
        for (name in names) {
            client.deleteTaildropFile(endpointTag, name)
        }
    }
}

private class ApiTailscaleSshCommand : ApiTailscaleCommandBase("ssh") {
    private val target by argument(name = "target", help = "[user@]<peer>")

    override fun help(context: Context) =
        "SSH into a Tailscale peer.\n\n" +
            "The local ssh binary is executed against the Tailscale address of the peer, so the machine running " +
            "this command must be able to route Tailscale addresses into the Tailscale endpoint itself, " +
            "usually by running behind a sing-box instance with a tun inbound.\n\n" +
            "The user defaults to the current user, and Tailscale SSH authentication is not available: " +
            "the peer is reached as an ordinary SSH server."

    override fun run() = withClient { client ->
        var loginName = ""
        var selector = target
        val atIndex = target.lastIndexOf('@')
        if (atIndex != -1) {
            loginName = target.substring(0, atIndex)
            selector = target.substring(atIndex + 1)
        }
        if (loginName.isEmpty()) {
            loginName = System.getProperty("user.name").orEmpty().substringAfter('\\')
        }
        val endpoint = fetchTailscaleEndpoint(client)
        val entry = resolvePeer(tailscalePeerEntries(endpoint), selector)
        val peerAddress = tailscalePeerAddress(entry.peer)
        if (peerAddress.isEmpty()) {
            echo("peer has no tailscale address: ${tailscalePeerName(entry.peer)}", err = true)
            throw ProgramResult(1)
        }
        val destination = if (loginName.isNotEmpty()) "$loginName@$peerAddress" else peerAddress
        val sshExecutable = resolveOnPath(if (PlatformInfo.isWindows) "ssh.exe" else "ssh")
        if (sshExecutable == null) {
            echo("ssh not found in PATH", err = true)
            throw ProgramResult(1)
        }
        val process = try {
            ProcessBuilder(sshExecutable.path, destination)
                .inheritIO()
                .start()
        } catch (e: Exception) {
            echo("failed to run ssh: ${e.message}", err = true)
            throw ProgramResult(1)
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw ProgramResult(exitCode)
        }
    }
}

internal data class TailscalePeerEntry(
    val peer: TailscalePeer,
    val group: TailscaleUserGroup? = null,
    val self: Boolean = false,
)

internal class PeerResolutionResult(
    val entry: TailscalePeerEntry? = null,
    val error: String? = null,
)

internal suspend fun fetchTailscaleStatus(client: CoreClient): List<TailscaleEndpointStatus> {
    val update = withTimeout(CLI_STREAM_TIMEOUT) {
        client.subscribeTailscaleStatus().first()
    }
    return update.endpointsList.sortedBy { it.endpointTag }
}

internal fun resolveTailscaleEndpointStatus(
    endpoints: List<TailscaleEndpointStatus>,
    endpointFlag: String,
): TailscaleEndpointStatus {
    if (endpoints.isEmpty()) {
        System.err.println("no tailscale endpoint found")
        throw ProgramResult(1)
    }
    if (endpointFlag.isNotEmpty()) {
        val endpoint = endpoints.firstOrNull { it.endpointTag == endpointFlag }
        if (endpoint == null) {
            System.err.println("unknown tailscale endpoint: $endpointFlag\nknown endpoints:\n${formatTailscaleEndpointTags(endpoints)}")
            throw ProgramResult(1)
        }
        return endpoint
    }
    if (endpoints.size > 1) {
        System.err.println("multiple tailscale endpoints, use --endpoint to select one:\n${formatTailscaleEndpointTags(endpoints)}")
        throw ProgramResult(1)
    }
    return endpoints[0]
}

private fun formatTailscaleEndpointTags(endpoints: List<TailscaleEndpointStatus>): String =
    endpoints.joinToString("\n") { "  ${it.endpointTag}" }

internal fun tailscalePeerEntries(endpoint: TailscaleEndpointStatus): List<TailscalePeerEntry> {
    val entries = mutableListOf<TailscalePeerEntry>()
    if (endpoint.hasSelf()) {
        entries += TailscalePeerEntry(peer = endpoint.self, self = true)
    }
    for (group in endpoint.userGroupsList) {
        for (peer in group.peersList) {
            entries += TailscalePeerEntry(peer = peer, group = group)
        }
    }
    return entries
}

internal fun tailscalePeerName(peer: TailscalePeer): String {
    var name = peer.dnsName.removeSuffix(".")
    if (name.isEmpty()) {
        name = peer.hostName
    }
    if (name.isEmpty()) {
        name = peer.stableID
    }
    return name
}

internal fun tailscalePeerAddress(peer: TailscalePeer): String =
    peer.tailscaleIPsList.firstOrNull().orEmpty()

internal fun resolveTailscalePeer(
    entries: List<TailscalePeerEntry>,
    selector: String,
): PeerResolutionResult {
    val selectorIp = parseIp(selector)
    val matchers: List<(TailscalePeer) -> Boolean> = listOf(
        // Matcher 1: stableID
        { peer -> peer.stableID == selector },
        // Matcher 2: IP address
        { peer ->
            if (selectorIp == null) {
                false
            } else {
                peer.tailscaleIPsList.any { ip -> parseIp(ip) == selectorIp }
            }
        },
        // Matcher 3: full DNS name or FQDN
        { peer ->
            val dnsName = peer.dnsName
            if (dnsName.isEmpty()) {
                false
            } else {
                dnsName.equals(selector, ignoreCase = true) ||
                    dnsName.removeSuffix(".").equals(selector, ignoreCase = true)
            }
        },
        // Matcher 4: first DNS label
        { peer ->
            val label = peer.dnsName.substringBefore('.')
            label.isNotEmpty() && label.equals(selector, ignoreCase = true)
        },
        // Matcher 5: host name
        { peer ->
            peer.hostName.isNotEmpty() && peer.hostName.equals(selector, ignoreCase = true)
        },
    )

    for (matcher in matchers) {
        val matches = entries.filter { matcher(it.peer) }
        if (matches.size == 1) {
            return PeerResolutionResult(entry = matches[0])
        }
        if (matches.size > 1) {
            return PeerResolutionResult(error = newTailscaleAmbiguousPeerError(selector, matches))
        }
    }
    return PeerResolutionResult(error = "peer not found: $selector")
}

private fun parseIp(text: String): InetAddress? {
    if (!text.isIpAddress()) return null
    return InetAddress.getByName(text)
}

private fun newTailscaleAmbiguousPeerError(selector: String, matches: List<TailscalePeerEntry>): String {
    val sortedMatches = matches.sortedBy { tailscalePeerName(it.peer).lowercase() }
    val names = sortedMatches.map { tailscalePeerName(it.peer) }
    val addresses = sortedMatches.map { tailscalePeerAddress(it.peer).ifEmpty { "-" } }
    val nameWidth = names.maxOf { it.length }
    val addressWidth = addresses.maxOf { it.length }
    return buildString {
        append("ambiguous peer: ")
        append(selector)
        for (index in sortedMatches.indices) {
            append("\n  ")
            append(names[index])
            append(" ".repeat(nameWidth - names[index].length + 3))
            append(addresses[index])
            append(" ".repeat(addressWidth - addresses[index].length + 3))
            append(sortedMatches[index].peer.stableID)
        }
    }
}

private fun formatYesNo(value: Boolean): String = if (value) "yes" else "no"

private fun formatTailscaleUser(group: TailscaleUserGroup?): String {
    if (group == null) return ""
    val loginName = group.loginName
    val displayName = group.displayName
    if (displayName.isEmpty() || displayName == loginName) {
        return loginName
    }
    if (loginName.isEmpty()) {
        return displayName
    }
    return "$loginName ($displayName)"
}

private fun formatTailscaleTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return formatApiTime(timestamp * 1000L)
}

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND
private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE

private const val TAILSCALE_PEER_LABEL_WIDTH = 16 // len("SSH host keys") + 3

private fun formatTailscaleSSHHostKeys(hostKeys: List<String>): String {
    if (hostKeys.isEmpty()) return ""
    val builder = StringBuilder()
    builder.append(hostKeys.size)
    for (hostKey in hostKeys) {
        builder.append('\n')
        builder.append(" ".repeat(TAILSCALE_PEER_LABEL_WIDTH))
        builder.append(hostKey)
    }
    return builder.toString()
}

/**
 * Mirrors sing-box: latency rounded to milliseconds, or to microseconds when that rounds to zero,
 * printed like Go's `time.Duration.String()`.
 */
private fun formatPingLatency(latencyMs: Double): String {
    val latencyMicros = (latencyMs * 1000).roundToLong()
    val roundedMillis = (latencyMicros + 500).floorDiv(1000L)
    if (roundedMillis == 0L) {
        return if (latencyMicros == 0L) {
            "0s"
        } else {
            "${latencyMicros}µs"
        }
    }
    val hours = roundedMillis / MILLIS_PER_HOUR
    val minutes = roundedMillis % MILLIS_PER_HOUR / MILLIS_PER_MINUTE
    val secondsMillis = roundedMillis % MILLIS_PER_MINUTE
    if (hours == 0L && minutes == 0L && secondsMillis < MILLIS_PER_SECOND) {
        return "${secondsMillis}ms"
    }
    val fraction = (secondsMillis % MILLIS_PER_SECOND).toString().padStart(3, '0').trimEnd('0')
    return buildString {
        if (hours > 0) append(hours).append('h')
        if (hours > 0 || minutes > 0) append(minutes).append('m')
        append(secondsMillis / MILLIS_PER_SECOND)
        if (fraction.isNotEmpty()) append('.').append(fraction)
        append('s')
    }
}

private fun formatTailscalePong(
    peerName: String,
    peerAddress: String,
    response: TailscalePingResponse,
): String {
    val via = when {
        response.endpoint.isNotEmpty() -> response.endpoint
        response.peerRelay.isNotEmpty() -> "peer relay ${response.peerRelay}"
        response.derpRegionCode.isNotEmpty() -> "DERP(${response.derpRegionCode})"
        else -> "DERP(${response.derpRegionID})"
    }
    val latency = formatPingLatency(response.latencyMs)
    return "pong from $peerName ($peerAddress) via $via in $latency"
}

internal fun formatTaildropSize(size: Long): String = when {
    size < 0 -> "-"
    size < 1_000 -> "$size B"
    size < 1_000_000 -> formatTaildropSizeUnit(size, 1_000L, "KB")
    size < 1_000_000_000 -> formatTaildropSizeUnit(size, 1_000_000L, "MB")
    else -> formatTaildropSizeUnit(size, 1_000_000_000L, "GB")
}

private fun formatTaildropSizeUnit(size: Long, unit: Long, unitName: String): String {
    val value = size.toDouble() / unit.toDouble()
    val precision = when {
        value < 10.0 -> 2
        value < 100.0 -> 1
        else -> 0
    }
    return String.format(Locale.US, "%.${precision}f %s", value, unitName)
}

private val TAILDROP_MODIFIED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatTaildropModifiedAt(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return Instant.ofEpochSecond(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(TAILDROP_MODIFIED_AT_FORMAT)
}
