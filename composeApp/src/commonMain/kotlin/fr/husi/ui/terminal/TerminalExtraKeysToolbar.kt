package fr.husi.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import fr.husi.compose.CapsuleDefaults
import fr.husi.compose.fadingEdge
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.content_paste
import fr.husi.resources.keyboard_arrow_down
import fr.husi.resources.keyboard_arrow_left
import fr.husi.resources.keyboard_arrow_right
import fr.husi.resources.keyboard_arrow_up
import fr.husi.resources.keyboard_tab
import io.github.xchacha20_poly1305.libghostty.GhosttyKey
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyExtraKey
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyExtraKeysDefaults
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyExtraKeysState
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyExtraKeysState.StickyState
import io.github.xchacha20_poly1305.libghostty.compose.GhosttyTerminalState
import io.github.xchacha20_poly1305.libghostty.compose.ghosttyExtraKey
import io.github.xchacha20_poly1305.libghostty.compose.rememberGhosttyExtraKeysState
import org.jetbrains.compose.resources.vectorResource

/**
 * The keys a software keyboard lacks, drawn as the floating toolbar of the config editor with the blur of a capsule:
 * it floats over the terminal and blurs the rows of [hazeState] behind it. While shown, its sticky Ctrl and Alt are
 * the terminal's [GhosttyTerminalState.stickyModifiers].
 */
@Composable
fun TerminalExtraKeysToolbar(
    state: GhosttyTerminalState,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    extraKeysState: GhosttyExtraKeysState = rememberGhosttyExtraKeysState(),
) {
    DisposableEffect(state, extraKeysState) {
        state.stickyModifiers = extraKeysState
        onDispose {
            if (state.stickyModifiers === extraKeysState) state.stickyModifiers = null
        }
    }
    val ctrlState by extraKeysState.ctrl.collectAsState()
    val altState by extraKeysState.alt.collectAsState()

    val shape = FloatingToolbarDefaults.ContainerShape
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier
            .clip(shape)
            .hazeBlur(
                input = HazeInput.Backdrop(hazeState),
                style = CapsuleDefaults.blurStyle(),
            )
            .border(1.dp, CapsuleDefaults.borderColor, shape),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = Color.Transparent,
            toolbarContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        ),
        shape = shape,
        // A shadow would show through the translucent container; the border outlines it like a capsule.
        expandedShadowElevation = 0.dp,
    ) {
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fadingEdge(
                    scrollableState = scrollState,
                    orientation = Orientation.Horizontal,
                    fadeStart = true,
                    fadeEnd = true,
                )
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (key in GhosttyExtraKeysDefaults.keys) when (key) {
                GhosttyExtraKey.Esc -> TextKey(state, "Esc") {
                    state.sendKey(GhosttyKey.ESCAPE)
                }

                GhosttyExtraKey.Tab -> IconKey(state, vectorResource(Res.drawable.keyboard_tab)) {
                    state.sendKey(GhosttyKey.TAB)
                }

                GhosttyExtraKey.Ctrl -> TextKey(state, "Ctrl", stickyState = ctrlState) {
                    extraKeysState.toggleCtrl()
                }

                GhosttyExtraKey.Alt -> TextKey(state, "Alt", stickyState = altState) {
                    extraKeysState.toggleAlt()
                }

                GhosttyExtraKey.ArrowLeft -> IconKey(
                    state,
                    vectorResource(Res.drawable.keyboard_arrow_left),
                    repeatable = true,
                ) {
                    state.sendKey(GhosttyKey.ARROW_LEFT)
                }

                GhosttyExtraKey.ArrowUp -> IconKey(
                    state,
                    vectorResource(Res.drawable.keyboard_arrow_up),
                    repeatable = true,
                ) {
                    state.sendKey(GhosttyKey.ARROW_UP)
                }

                GhosttyExtraKey.ArrowDown -> IconKey(
                    state,
                    vectorResource(Res.drawable.keyboard_arrow_down),
                    repeatable = true,
                ) {
                    state.sendKey(GhosttyKey.ARROW_DOWN)
                }

                GhosttyExtraKey.ArrowRight -> IconKey(
                    state,
                    vectorResource(Res.drawable.keyboard_arrow_right),
                    repeatable = true,
                ) {
                    state.sendKey(GhosttyKey.ARROW_RIGHT)
                }

                is GhosttyExtraKey.Symbol -> TextKey(state, key.text) {
                    state.sendText(key.text)
                }

                GhosttyExtraKey.Paste -> IconKey(state, vectorResource(Res.drawable.content_paste)) {
                    state.paste()
                }

                GhosttyExtraKey.Divider -> VerticalDivider(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(24.dp),
                )
            }
        }
    }
}

@Composable
private fun TextKey(
    state: GhosttyTerminalState,
    label: String,
    stickyState: StickyState = StickyState.INACTIVE,
    onPress: () -> Unit,
) {
    KeyButton(state, stickyState = stickyState, onPress = onPress) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

@Composable
private fun IconKey(
    state: GhosttyTerminalState,
    imageVector: ImageVector,
    repeatable: Boolean = false,
    onPress: () -> Unit,
) {
    KeyButton(state, repeatable = repeatable, onPress = onPress) {
        Icon(imageVector = imageVector, contentDescription = null)
    }
}

/** A round toolbar button; a sticky modifier fills it while armed and fills it stronger while locked. */
@Composable
private fun KeyButton(
    state: GhosttyTerminalState,
    repeatable: Boolean = false,
    stickyState: StickyState = StickyState.INACTIVE,
    onPress: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (stickyState) {
        StickyState.INACTIVE -> Color.Transparent
        StickyState.ARMED -> colorScheme.secondaryContainer
        StickyState.LOCKED -> colorScheme.primary
    }
    val contentColor = when (stickyState) {
        StickyState.INACTIVE -> LocalContentColor.current
        StickyState.ARMED -> colorScheme.onSecondaryContainer
        StickyState.LOCKED -> colorScheme.onPrimary
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .indication(interactionSource, ripple())
            .ghosttyExtraKey(
                state = state,
                repeatable = repeatable,
                interactionSource = interactionSource,
                onPress = onPress,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}
