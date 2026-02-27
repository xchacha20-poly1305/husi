package fr.husi.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import fr.husi.compose.BoxedVerticalScrollbar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.SimpleTopAppBar
import fr.husi.compose.TextButton
import fr.husi.compose.withNavigation
import fr.husi.ktx.emptyAsNull
import fr.husi.resources.Res
import fr.husi.resources.arrow_back
import fr.husi.resources.back
import fr.husi.resources.copyright
import fr.husi.resources.ok
import fr.husi.resources.oss_licenses
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun LibrariesScreen(
    onBackPress: () -> Unit,
) {
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    var showLibraryDialog by remember { mutableStateOf<Library?>(null) }
    val uriHandler = LocalUriHandler.current

    val libraries by produceLibraries {
        Res.readBytes("files/aboutlibraries.json").decodeToString()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.oss_licenses)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            val contentPadding = innerPadding.withNavigation()
            LibrariesContainer(
                libraries = libraries,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                lazyListState = listState,
                contentPadding = contentPadding,
                onLibraryClick = { library ->
                    if (library.strippedLicenseContent.isNotBlank()) {
                        showLibraryDialog = library
                    } else {
                        val url = library.licenses.firstOrNull()?.url
                        if (!url.isNullOrBlank()) {
                            runCatching { uriHandler.openUri(url) }
                        }
                    }
                },
            )
            BoxedVerticalScrollbar(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState),
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 12.dp,
                ),
            )
        }
    }

    showLibraryDialog?.let { library ->
        LibrariesLicenseDialog(
            library = library,
            onDismiss = { showLibraryDialog = null },
        )
    }
}

@Composable
private fun LibrariesLicenseDialog(
    library: Library,
    onDismiss: () -> Unit,
) {
    val license = remember(library) {
        library.strippedLicenseContent.emptyAsNull()
    } ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) { onDismiss() }
        },
        icon = { Icon(vectorResource(Res.drawable.copyright), null) },
        title = {
            SelectionContainer {
                Text(library.name)
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Row {
                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scrollState),
                ) {
                    Text(
                        text = license,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                BoxedVerticalScrollbar(
                    modifier = Modifier.fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = scrollState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        },
    )
}
