package fr.husi.ui.tailscale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.DropdownMenuSectionHeader
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.ScrollableDropdownMenuPopup
import fr.husi.compose.material3.Text
import fr.husi.ktx.blankAsNull
import fr.husi.resources.Res
import fr.husi.resources.add
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.close
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.tailscale_ssh_close_session
import fr.husi.resources.tailscale_ssh_connecting
import fr.husi.resources.tailscale_ssh_new_session
import fr.husi.resources.tailscale_ssh_sessions
import fr.husi.tailscale.TailscaleSSHPhase
import fr.husi.tailscale.TailscaleSSHSession
import fr.husi.tailscale.TailscaleSSHSessionManager
import fr.husi.ui.terminal.TerminalPane
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyDialogs
import io.github.xchacha20_poly1305.libghostty.compose.rememberGhosttyTerminalState
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject

@Composable
fun TailscaleSSHTerminalScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    manager: TailscaleSSHSessionManager = koinInject(),
) {
    val state by manager.state.collectAsStateWithLifecycle()
    val activeSession = state.activeSession
    val terminalState = rememberGhosttyTerminalState()
    var isOverflowMenuExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.sessions.isEmpty()) {
        if (state.sessions.isEmpty()) onClose()
    }

    val windowInsets = WindowInsets.safeDrawing
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CapsuleTopBar(
                hazeState = null,
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onClose,
                    )
                },
                title = {
                    if (activeSession != null) {
                        Text(sessionTitle(activeSession))
                    }
                },
                actions = {
                    CapsuleActionButton {
                        Box {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.more_vert),
                                contentDescription = stringResource(Res.string.more),
                                onClick = { isOverflowMenuExpanded = true },
                            )
                            SessionMenu(
                                expanded = isOverflowMenuExpanded,
                                onDismissRequest = { isOverflowMenuExpanded = false },
                                sessions = state.sessions,
                                activeSession = activeSession,
                                manager = manager,
                            )
                        }
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
    ) { innerPadding ->
        if (activeSession == null) return@Scaffold
        TerminalPane(
            session = activeSession.terminal,
            state = terminalState,
            onFinishedSessionKey = { manager.close(activeSession.id) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            overlay = { SessionStatus(activeSession) },
        )
    }

    GhosttyDialogs(terminalState)
}

@Composable
private fun SessionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    sessions: List<TailscaleSSHSession>,
    activeSession: TailscaleSSHSession?,
    manager: TailscaleSSHSessionManager,
) {
    val groupCount = if (sessions.size > 1) {
        2
    } else {
        1
    }
    ScrollableDropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(0, groupCount),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.tailscale_ssh_new_session)) },
                onClick = {
                    onDismissRequest()
                    activeSession?.let { manager.duplicate(it.id) }
                },
                leadingIcon = { Icon(vectorResource(Res.drawable.add), null) },
                shape = MenuDefaults.itemShape(0, 2).shape,
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.tailscale_ssh_close_session)) },
                onClick = {
                    onDismissRequest()
                    activeSession?.let { manager.close(it.id) }
                },
                leadingIcon = { Icon(vectorResource(Res.drawable.close), null) },
                shape = MenuDefaults.itemShape(1, 2).shape,
            )
        }
        if (groupCount > 1) {
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(1, groupCount),
            ) {
                DropdownMenuSectionHeader(stringResource(Res.string.tailscale_ssh_sessions))
                sessions.forEachIndexed { index, session ->
                    DropdownMenuItem(
                        text = { Text(sessionTitle(session)) },
                        onClick = {
                            onDismissRequest()
                            manager.select(session.id)
                        },
                        trailingIcon = {
                            RadioButton(
                                selected = session.id == activeSession?.id,
                                onClick = null,
                            )
                        },
                        shape = MenuDefaults.itemShape(index, sessions.size).shape,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionStatus(session: TailscaleSSHSession) {
    val phase by session.phase.collectAsStateWithLifecycle()
    val banner by session.banner.collectAsStateWithLifecycle()
    val connecting = phase == TailscaleSSHPhase.CONNECTING
    val showBanner = !banner.isNullOrBlank() && phase != TailscaleSSHPhase.RUNNING
    if (!connecting && !showBanner) return
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (connecting) {
            CircularProgressIndicator()
            Text(
                text = stringResource(Res.string.tailscale_ssh_connecting),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (showBanner) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.widthIn(max = 480.dp),
            ) {
                Text(
                    text = remember(banner) { bannerText(banner.orEmpty()) },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun sessionTitle(session: TailscaleSSHSession): String {
    val terminalTitle by session.terminal.title.collectAsStateWithLifecycle()
    return terminalTitle.blankAsNull() ?: session.target.peerHostName
}

private val bannerUrlSchemes = listOf("http://", "https://")

/** Tailscale check mode asks for a login through a URL in the banner, so URLs are links. */
private fun bannerText(text: String) = buildAnnotatedString {
    var lastIndex = 0
    while (true) {
        val urlStart = bannerUrlSchemes
            .map { text.indexOf(it, lastIndex) }
            .filter { it >= 0 }
            .minOrNull()
            ?: break
        var urlEnd = urlStart
        while (urlEnd < text.length && !text[urlEnd].isWhitespace()) {
            urlEnd++
        }
        append(text, lastIndex, urlStart)
        val url = text.substring(urlStart, urlEnd)
        withLink(LinkAnnotation.Url(url)) {
            append(url)
        }
        lastIndex = urlEnd
    }
    append(text, lastIndex, text.length)
}
