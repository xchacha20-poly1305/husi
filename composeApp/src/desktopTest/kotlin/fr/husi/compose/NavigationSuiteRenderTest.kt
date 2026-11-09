package fr.husi.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import fr.husi.compose.material3.NavigationSuite
import fr.husi.compose.material3.NavigationSuiteItem
import fr.husi.resources.Res
import fr.husi.resources.bug_report
import fr.husi.resources.description
import fr.husi.resources.directions
import fr.husi.resources.menu_configuration
import fr.husi.resources.menu_dashboard
import fr.husi.resources.menu_log
import fr.husi.resources.menu_route
import fr.husi.resources.settings
import fr.husi.resources.transform
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class NavigationSuiteRenderTest {
    @Test
    fun `resizing across the breakpoint preserves content and moves navigation`() {
        NavigationScene(width = 599).use { scene ->
            scene.renderUntilIdle()
            val input = scene.input
            input.value = "unsaved input"
            assertEquals(0f, scene.contentBounds.left)
            assertTrue(scene.contentBounds.bottom < scene.height)
            scene.assertOverlaysInsideContent()

            scene.resize(width = 600)
            assertTrue(scene.contentBounds.left > 0)
            assertEquals(scene.height.toFloat(), scene.contentBounds.bottom)
            scene.assertOverlaysInsideContent()
            assertSame(input, scene.input)
            assertEquals("unsaved input", scene.input.value)

            scene.resize(width = 599)
            assertEquals(0f, scene.contentBounds.left)
            assertTrue(scene.contentBounds.bottom < scene.height)
            assertSame(input, scene.input)
        }
    }

    @Test
    fun `pushed pages retain the rail but hide the bottom bar`() {
        NavigationScene(width = 600).use { scene ->
            scene.renderUntilIdle()
            val input = scene.input
            scene.isTopLevel = false
            scene.renderUntilIdle()
            assertTrue(scene.contentBounds.left > 0)

            scene.resize(width = 599)
            assertEquals(Rect(0f, 0f, 599f, scene.height.toFloat()), scene.contentBounds)
            scene.assertNoNavigationActions()
            assertSame(input, scene.input)

            scene.isTopLevel = true
            scene.renderUntilIdle()
            assertTrue(scene.contentBounds.bottom < scene.height)
        }
    }

    @Test
    fun `setup occupies the full window until navigation is available`() {
        NavigationScene(width = 600, initialNavigationAvailable = false).use { scene ->
            scene.renderUntilIdle()
            assertEquals(Rect(0f, 0f, 600f, scene.height.toFloat()), scene.contentBounds)
            scene.assertNoNavigationActions()
            scene.resize(width = 599)
            assertEquals(Rect(0f, 0f, 599f, scene.height.toFloat()), scene.contentBounds)
            scene.assertNoNavigationActions()

            scene.navigationAvailable = true
            scene.renderUntilIdle()
            assertTrue(scene.contentBounds.bottom < scene.height)
            scene.resize(width = 600)
            assertTrue(scene.contentBounds.left > 0)

            scene.navigationAvailable = false
            scene.renderUntilIdle()
            assertEquals(Rect(0f, 0f, 600f, scene.height.toFloat()), scene.contentBounds)
            scene.assertNoNavigationActions()
        }
    }

    @Test
    fun `short wide windows still use a rail and respect RTL`() {
        NavigationScene(width = 800, height = 360, layoutDirection = LayoutDirection.Rtl).use { scene ->
            scene.renderUntilIdle()
            assertEquals(0f, scene.contentBounds.left)
            assertTrue(scene.contentBounds.right < 800)
            assertEquals(360f, scene.contentBounds.bottom)
            scene.assertOverlaysInsideContent()
            scene.scrollToSettingsAndClick()
        }
    }

    private class NavigationScene(
        width: Int,
        val height: Int = 800,
        initialNavigationAvailable: Boolean = true,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) : AutoCloseable {
        var isTopLevel by mutableStateOf(true)
        var navigationAvailable by mutableStateOf(initialNavigationAvailable)
        lateinit var input: MutableState<String>
        var contentBounds = Rect.Zero
        private var snackbarBounds = Rect.Zero
        private var fabBounds = Rect.Zero
        private var settingsLabel = ""
        private var settingsClicked = false
        private var frameTime = 0L
        private val scene = ImageComposeScene(
            width = width,
            height = height,
            density = Density(1f),
            layoutDirection = layoutDirection,
        )

        init {
            scene.setContent {
                MaterialTheme {
                    val label = stringResource(Res.string.settings)
                    SideEffect { settingsLabel = label }
                    NavigationSuite(
                        items = persistentListOf(
                            NavigationSuiteItem(Res.string.menu_configuration, Res.drawable.description, true, {}),
                            NavigationSuiteItem(Res.string.menu_dashboard, Res.drawable.transform, false, {}),
                            NavigationSuiteItem(Res.string.menu_route, Res.drawable.directions, false, {}),
                            NavigationSuiteItem(Res.string.menu_log, Res.drawable.bug_report, false, {}),
                            NavigationSuiteItem(Res.string.settings, Res.drawable.settings, false, {
                                settingsClicked = true
                            }),
                        ),
                        isTopLevel = isTopLevel,
                        navigationAvailable = navigationAvailable,
                        snackbarHost = {
                            Box(Modifier.size(200.dp, 48.dp).onGloballyPositioned {
                                snackbarBounds = it.boundsInRoot()
                            })
                        },
                        floatingActionButton = {
                            Box(Modifier.size(56.dp).onGloballyPositioned {
                                fabBounds = it.boundsInRoot()
                            })
                        },
                    ) {
                        val draft = remember { mutableStateOf("") }
                        SideEffect { input = draft }
                        Box(Modifier.fillMaxSize().onGloballyPositioned {
                            contentBounds = it.boundsInRoot()
                        })
                    }
                }
            }
        }

        fun resize(width: Int) {
            scene.constraints = Constraints.fixed(width, height)
            renderUntilIdle()
        }

        fun renderUntilIdle() {
            Snapshot.sendApplyNotifications()
            repeat(120) {
                frameTime += 16_666_667L
                scene.render(frameTime).close()
                if (!scene.hasInvalidations()) return
            }
            error("Navigation layout did not settle")
        }

        fun assertOverlaysInsideContent() {
            for (bounds in listOf(snackbarBounds, fabBounds)) {
                assertTrue(bounds.width > 0 && bounds.height > 0)
                assertTrue(bounds.left >= contentBounds.left)
                assertTrue(bounds.right <= contentBounds.right)
                assertTrue(bounds.bottom <= contentBounds.bottom)
            }
            assertTrue(snackbarBounds.bottom <= fabBounds.top)
        }

        fun scrollToSettingsAndClick() {
            val scroll = scene.semanticsOwners
                .flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                .firstNotNullOf { it.config.getOrNull(SemanticsActions.ScrollBy)?.action }
            assertTrue(scroll(0f, 1000f))
            renderUntilIdle()
            val settings = scene.semanticsOwners
                .flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                .first { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any {
                        it.text == settingsLabel
                    } == true && node.config.contains(SemanticsActions.OnClick)
                }
            assertTrue(settings.boundsInRoot.height >= 48f)
            assertTrue(settings.boundsInRoot.top >= 0f)
            assertTrue(settings.boundsInRoot.bottom <= height)
            assertTrue(settings.config[SemanticsActions.OnClick].action!!.invoke())
            assertTrue(settingsClicked)
        }

        fun assertNoNavigationActions() {
            assertTrue(scene.semanticsOwners
                .flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                .none { it.config.contains(SemanticsActions.OnClick) })
        }

        override fun close() = scene.close()
    }
}
