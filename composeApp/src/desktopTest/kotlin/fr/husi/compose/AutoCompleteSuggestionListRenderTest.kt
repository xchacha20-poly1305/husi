package fr.husi.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Dropdown menus measure their content with intrinsic measurements, which lazy layouts do not
 * support. See https://github.com/xchacha20-poly1305/husi/issues/845.
 */
@OptIn(ExperimentalComposeUiApi::class)
class AutoCompleteSuggestionListRenderTest {

    private val suggestions = List(50) { "geosite-suggestion-$it" }

    private fun render(content: @Composable () -> Unit) {
        val scene = ImageComposeScene(
            width = 400,
            height = 800,
            density = Density(1f),
        )
        try {
            scene.setContent {
                // How Material3 lays out the content of a dropdown menu.
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
            }
            scene.render()
        } finally {
            scene.close()
        }
    }

    @Test
    fun `suggestion list survives intrinsic measurements`() {
        render {
            AutoCompleteSuggestionList(
                suggestions = suggestions,
                selectedIndex = 0,
                onChooseSuggestion = {},
            ) { suggestion ->
                Text(suggestion)
            }
        }
    }

    @Test
    fun `lazy list does not survive intrinsic measurements`() {
        assertFailsWith<IllegalStateException> {
            render {
                LazyColumn {
                    items(suggestions) { suggestion ->
                        Text(suggestion)
                    }
                }
            }
        }
    }
}
