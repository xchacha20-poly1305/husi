package fr.husi.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.collection.ArraySet
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarDuration
import fr.husi.compose.SwipeableSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import fr.husi.compose.CapsuleActionButton
import fr.husi.compose.CapsuleTopBar
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.material3.Card
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Surface
import fr.husi.compose.material3.Switch
import fr.husi.compose.material3.Text
import fr.husi.compose.setPlainText
import fr.husi.compose.withNavigation
import fr.husi.ktx.blankAsNull
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.action_copy
import fr.husi.resources.action_import
import fr.husi.resources.action_import_err
import fr.husi.resources.action_import_msg
import fr.husi.resources.cancel
import fr.husi.resources.close
import fr.husi.resources.content_paste
import fr.husi.resources.copy_all
import fr.husi.resources.copy_success
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.ok
import fr.husi.resources.search
import fr.husi.utils.PackageCache
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Immutable
internal data class ProxiedApp(
    private val appInfo: ApplicationInfo,
    val packageName: String,
    var isProxied: Boolean,
    val icon: Drawable,
    val name: String, // cached for sorting
) {
    val uid get() = appInfo.uid
}

@Stable
internal abstract class BaseAppListViewModel : ViewModel() {
    val textFieldState = TextFieldState()

    protected lateinit var packageManager: PackageManager
    protected lateinit var appPackageName: String
    protected val proxiedUids = ArraySet<Int>()
    protected val singleThreadContext = Dispatchers.IO.limitedParallelism(1)
    protected val cachedApps by lazy {
        PackageCache.installedPackages.toMutableMap().apply {
            remove(appPackageName)
        }
    }

    private val iconCache = mutableMapOf<String, Drawable>()
    private fun loadIcon(packageInfo: PackageInfo): Drawable {
        return iconCache.getOrPut(packageInfo.packageName) {
            packageInfo.applicationInfo!!.loadIcon(packageManager)
        }
    }

    protected abstract fun updateApps(
        apps: List<ProxiedApp>,
        filteredApps: List<ProxiedApp>,
        isLoading: Boolean,
    )

    protected abstract fun updateSnackbar(message: StringOrRes?)
    protected abstract suspend fun afterMutation()
    protected abstract suspend fun afterItemClick(app: ProxiedApp, newIsProxied: Boolean)
    abstract fun export(): String

    protected fun collectSearchText() {
        viewModelScope.launch {
            snapshotFlow { textFieldState.text.toString() }
                .drop(1)
                .distinctUntilChanged()
                .collect { withContext(singleThreadContext) { reload() } }
        }
    }

    suspend fun reload(cachedApps: Map<String, PackageInfo> = this.cachedApps) {
        val allApps = mutableListOf<ProxiedApp>()
        for ((packageName, packageInfo) in cachedApps) {
            currentCoroutineContext()[Job]!!.ensureActive()

            val applicationInfo = packageInfo.applicationInfo!!
            val name = applicationInfo.loadLabel(packageManager).toString()
            allApps.add(
                ProxiedApp(
                    appInfo = applicationInfo,
                    packageName = packageName,
                    isProxied = proxiedUids.contains(applicationInfo.uid),
                    icon = loadIcon(packageInfo),
                    name = name,
                ),
            )
        }
        allApps.sortWith(compareBy({ !it.isProxied }, { it.name }))

        val query = textFieldState.text.toString().blankAsNull()?.lowercase()
        val filteredApps = if (query == null) {
            allApps
        } else {
            allApps.filter { app ->
                app.packageName.lowercase().contains(query)
                        || app.name.lowercase().contains(query)
                        || app.uid.toString().contains(query)
            }
        }
        updateApps(allApps, filteredApps, false)
    }

    fun invertSections() = viewModelScope.launch(singleThreadContext) {
        val current = ArraySet(proxiedUids)
        val cachedApps = cachedApps
        val allUids = ArraySet<Int>()
        for ((_, packageInfo) in cachedApps) {
            allUids.add(packageInfo.applicationInfo!!.uid)
        }
        proxiedUids.clear()
        proxiedUids.ensureCapacity(cachedApps.size - current.size)
        allUids.filter { it !in current }.forEach { proxiedUids.add(it) }
        afterMutation()
    }

    fun clearSections() = viewModelScope.launch(singleThreadContext) {
        proxiedUids.clear()
        afterMutation()
    }

    fun import(raw: String?) = viewModelScope.launch(singleThreadContext) {
        if (raw?.blankAsNull() == null) {
            updateSnackbar(StringOrRes.Res(Res.string.action_import_err))
            return@launch
        }
        var bypass = false
        val apps = raw.lineSequence().let {
            when (it.firstOrNull()) {
                "false" -> {
                    bypass = false
                    it.drop(1)
                }

                "true" -> {
                    bypass = true
                    it.drop(1)
                }

                else -> it
            }
        }
        applyImport(bypass, apps)
        updateSnackbar(StringOrRes.Res(Res.string.action_import_msg))
        afterMutation()
    }

    protected open suspend fun applyImport(bypass: Boolean, apps: Sequence<String>) {
        proxiedUids.clear()
        val cachedApps = cachedApps
        if (bypass) {
            val bypassSet = apps.toSet()
            for ((packageName, packageInfo) in cachedApps) {
                if (packageName !in bypassSet) {
                    proxiedUids.add(packageInfo.applicationInfo!!.uid)
                }
            }
        } else {
            for (packageName in apps) {
                val info = cachedApps[packageName] ?: continue
                proxiedUids.add(info.applicationInfo!!.uid)
            }
        }
    }

    fun onItemClick(app: ProxiedApp) = viewModelScope.launch(singleThreadContext) {
        val newIsProxied = !proxiedUids.remove(app.uid)
        if (newIsProxied) {
            proxiedUids.add(app.uid)
        }
        afterItemClick(app, newIsProxied)
    }
}

@Composable
internal fun AppListContent(
    apps: List<ProxiedApp>,
    innerPadding: PaddingValues,
    onClick: (ProxiedApp) -> Unit,
    scrollState: LazyListState = rememberLazyListState(),
) {
    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            state = scrollState,
            contentPadding = innerPadding.withNavigation(),
        ) {
            items(
                items = apps,
                key = { it.packageName },
                contentType = { 0 },
            ) { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = rememberDrawablePainter(app.icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(end = 12.dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .width(0.dp),
                        ) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "${app.packageName} (${app.uid})",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = app.isProxied,
                            onCheckedChange = { onClick(app) },
                        )
                    }
                }
            }
        }

        Box {
            VerticalScrollbar(
                modifier = Modifier.fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = scrollState),
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 16.dp,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppListScaffold(
    viewModel: BaseAppListViewModel,
    title: @Composable () -> Unit,
    isLoading: Boolean,
    apps: List<ProxiedApp>,
    filteredApps: List<ProxiedApp>,
    snackbarMessage: StringOrRes?,
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraTopBarContent: @Composable () -> Unit = {},
    dropdownMenuItems: @Composable (onDismiss: () -> Unit) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = getStringOrRes(message),
                actionLabel = resolveRepository().getString(Res.string.ok),
                duration = SnackbarDuration.Short,
            )
        }
    }

    val searchBarState = rememberSearchBarState()
    val textFieldState = viewModel.textFieldState
    val searchInputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = {
                scope.launch {
                    searchBarState.animateToCollapsed()
                }
            },
            leadingIcon = {
                Icon(vectorResource(Res.drawable.search), null)
            },
            trailingIcon = if (searchBarState.currentValue == SearchBarValue.Expanded) {
                {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.cancel),
                        onClick = {
                            textFieldState.setTextAndPlaceCursorAtEnd("")
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                    )
                }
            } else {
                null
            },
        )
    }

    var showMoreActions by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listScrollState = rememberLazyListState()
    val windowInsets = WindowInsets.safeDrawing
    val topAppBarColors = TopAppBarDefaults.topAppBarColors()
    val appBarContainerColor by animateColorAsState(
        targetValue = lerp(
            topAppBarColors.containerColor,
            topAppBarColors.scrolledContainerColor,
            scrollBehavior.state.overlappedFraction.fastCoerceIn(0f, 1f),
        ),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appBarContainerColor",
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Surface(
                color = appBarContainerColor,
            ) {
                Column {
                    CapsuleTopBar(
                        navigationIcon = {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.close),
                                contentDescription = stringResource(Res.string.close),
                                onClick = onNavigationClick,
                            )
                        },
                        title = title,
                        actions = {
                            CapsuleActionButton {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.copy_all),
                                    contentDescription = stringResource(Res.string.action_copy),
                                    onClick = {
                                        val toExport = viewModel.export()
                                        scope.launch {
                                            clipboard.setPlainText(toExport)
                                            snackbarHostState.showSnackbar(
                                                message = resolveRepository().getString(Res.string.copy_success),
                                                actionLabel = resolveRepository().getString(Res.string.ok),
                                                duration = SnackbarDuration.Short,
                                            )
                                        }
                                    },
                                )
                            }
                            CapsuleActionButton {
                                SimpleIconButton(
                                    imageVector = vectorResource(Res.drawable.content_paste),
                                    contentDescription = stringResource(Res.string.action_import),
                                    onClick = {
                                        scope.launch {
                                            val text = clipboard.getClipEntry()?.clipData
                                                ?.getItemAt(0)?.text
                                                ?.toString()
                                            viewModel.import(text)
                                        }
                                    },
                                )
                            }
                            CapsuleActionButton {
                                Box {
                                    SimpleIconButton(
                                        imageVector = vectorResource(Res.drawable.more_vert),
                                        contentDescription = stringResource(Res.string.more),
                                        onClick = { showMoreActions = true },
                                    )

                                    DropdownMenu(
                                        expanded = showMoreActions,
                                        onDismissRequest = { showMoreActions = false },
                                        shape = MenuDefaults.standaloneGroupShape,
                                        containerColor = MenuDefaults.groupStandardContainerColor,
                                    ) {
                                        dropdownMenuItems { showMoreActions = false }
                                    }
                                }
                            }
                        },
                        windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                        scrollBehavior = scrollBehavior,
                    )

                    SearchBar(
                        state = searchBarState,
                        inputField = searchInputField,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    extraTopBarContent()
                }
            }
        },
        snackbarHost = { SwipeableSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Crossfade(
            targetState = isLoading,
            animationSpec = tween(durationMillis = 300),
        ) { loading ->
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                AppListContent(
                    apps = apps,
                    scrollState = listScrollState,
                    innerPadding = innerPadding,
                    onClick = { viewModel.onItemClick(it) },
                )
            }
        }
    }
    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = searchInputField,
    ) {
        AppListContent(
            apps = filteredApps,
            innerPadding = PaddingValues(),
            onClick = { viewModel.onItemClick(it) },
        )
    }
}
