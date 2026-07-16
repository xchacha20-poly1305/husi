package fr.husi.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ernestoyaquello.dragdropswipelazycolumn.DragDropSwipeLazyColumn
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItem
import com.ernestoyaquello.dragdropswipelazycolumn.config.DraggableSwipeableItemColors
import fr.husi.compose.DurationTextField
import fr.husi.compose.IconMaskColors
import fr.husi.compose.IconMaskShapes
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PreferenceDivider
import fr.husi.compose.TooltipIconButton
import fr.husi.compose.UIntegerTextField
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.database.ProxyEntity
import fr.husi.database.displayType
import fr.husi.fmt.internal.ProxySetBean
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.intListN
import fr.husi.resources.Res
import fr.husi.resources.action_selector
import fr.husi.resources.action_urltest
import fr.husi.resources.add_profile
import fr.husi.resources.cast_connected
import fr.husi.resources.connection_test_url
import fr.husi.resources.delete
import fr.husi.resources.delete_sweep
import fr.husi.resources.edit
import fr.husi.resources.emoji_emotions
import fr.husi.resources.emoji_symbols
import fr.husi.resources.filter_regex
import fr.husi.resources.flip_camera_android
import fr.husi.resources.group_settings
import fr.husi.resources.group_type
import fr.husi.resources.idle_timeout
import fr.husi.resources.interrupt_exist_connections
import fr.husi.resources.list
import fr.husi.resources.management
import fr.husi.resources.menu_group
import fr.husi.resources.nfc
import fr.husi.resources.not_set
import fr.husi.resources.photo_camera
import fr.husi.resources.profile_name
import fr.husi.resources.stop
import fr.husi.resources.urltest_interval
import fr.husi.resources.urltest_tolerance
import fr.husi.resources.view_list
import fr.husi.resources.widgets
import fr.husi.ui.NavRoutes
import fr.husi.ui.OpenProfilePicker
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySetSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onOpenProfileSelect: OpenProfilePicker,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: ProxySetSettingsViewModel =
        profileEditorViewModel(profileId = profileId, isSubscription = isSubscription) {
            ProxySetSettingsViewModel()
        }

    ProfileSettingsScreenScaffold(
        title = Res.string.group_settings,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        proxySetSettings(
            uiState = uiState as ProxySetUiState,
            viewModel = viewModel,
            onAdd = {
                viewModel.replacing = -1
                onOpenProfileSelect(null) { id ->
                    viewModel.replacing = -1
                    viewModel.onSelectProfile(id)
                }
            },
            onReplace = { index, selectedProfileId ->
                viewModel.replacing = index
                onOpenProfileSelect(selectedProfileId.takeIf { it > 0 }) { id ->
                    viewModel.onSelectProfile(id)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun LazyListScope.proxySetSettings(
    uiState: ProxySetUiState,
    viewModel: ProxySetSettingsViewModel,
    onAdd: () -> Unit,
    onReplace: (index: Int, profileId: Long) -> Unit,
) {
    preferenceGroup {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = { MaskedIcon(Res.drawable.emoji_symbols) },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
        PreferenceDivider()
        fun managementName(management: Int) =
            when (management) {
                ProxySetBean.MANAGEMENT_SELECTOR -> Res.string.action_selector
                ProxySetBean.MANAGEMENT_URLTEST -> Res.string.action_urltest
                else -> error("impossible")
            }
        ListPreference(
            value = uiState.management,
            onValueChange = { viewModel.setManagement(it) },
            values = intListN(2),
            title = { Text(stringResource(Res.string.management)) },
            icon = { MaskedIcon(Res.drawable.widgets, IconMaskColors.IconLavender) },
            summary = { Text(stringResource(managementName(uiState.management))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(stringResource(managementName(it))) },
        )
        PreferenceDivider()
        SwitchPreference(
            value = uiState.interruptExistConnections,
            onValueChange = { viewModel.setInterruptExistConnections(it) },
            title = { Text(stringResource(Res.string.interrupt_exist_connections)) },
            icon = { MaskedIcon(Res.drawable.stop, IconMaskColors.IconCoral) },
        )
        if (uiState.management == ProxySetBean.MANAGEMENT_URLTEST) {
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.testURL,
                onValueChange = { viewModel.setTestURL(it) },
                title = { Text(stringResource(Res.string.connection_test_url)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.cast_connected,
                        color = IconMaskColors.IconLightOrange,
                        shape = IconMaskShapes.route(),
                    )
                },
                summary = { Text(contentOrUnset(uiState.testURL)) },
                valueToText = { it },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.testInterval,
                onValueChange = { viewModel.setTestInterval(it) },
                title = { Text(stringResource(Res.string.urltest_interval)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.flip_camera_android,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
                summary = { Text(contentOrUnset(uiState.testInterval)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    DurationTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.testIdleTimeout,
                onValueChange = { viewModel.setTestIdleTimeout(it) },
                title = { Text(stringResource(Res.string.idle_timeout)) },
                textToValue = { it },
                icon = { MaskedIcon(Res.drawable.photo_camera, IconMaskColors.IconWarmGray) },
                summary = { Text(contentOrUnset(uiState.testIdleTimeout)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    DurationTextField(value, onValueChange, onOk)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.testTolerance,
                onValueChange = { viewModel.setTestTolerance(it) },
                title = { Text(stringResource(Res.string.urltest_tolerance)) },
                textToValue = { it.toIntOrNull() ?: 50 },
                icon = { MaskedIcon(Res.drawable.emoji_emotions, IconMaskColors.IconLightGreen) },
                summary = { Text(uiState.testTolerance.toString()) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
        }
        PreferenceDivider()
        fun typeName(type: Int) =
            when (type) {
                ProxySetBean.TYPE_LIST -> Res.string.list
                ProxySetBean.TYPE_GROUP -> Res.string.menu_group
                else -> error("impossible")
            }
        ListPreference(
            value = uiState.collectType,
            onValueChange = { viewModel.setCollectType(it) },
            values = intListN(2),
            title = { Text(stringResource(Res.string.group_type)) },
            icon = { MaskedIcon(Res.drawable.nfc, IconMaskColors.IconLightPink) },
            summary = { Text(stringResource(typeName(uiState.collectType))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(stringResource(typeName(it))) },
        )
        if (uiState.collectType == ProxySetBean.TYPE_GROUP) {
            PreferenceDivider()
            ListPreference(
                value = uiState.groupID,
                onValueChange = { viewModel.setGroupID(it) },
                values = uiState.groups.keys.toList(),
                title = { Text(stringResource(Res.string.menu_group)) },
                icon = { MaskedIcon(Res.drawable.view_list, IconMaskColors.IconLightYellow) },
                summary = {
                    val text = uiState.groups[uiState.groupID]?.displayName()
                            ?: stringResource(Res.string.not_set)
                    Text(text)
                },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { id ->
                    val text =
                        uiState.groups[id]?.displayName() ?: stringResource(Res.string.not_set)
                    AnnotatedString(text)
                },
            )
            PreferenceDivider()
            TextFieldPreference(
                value = uiState.filterNotRegex,
                onValueChange = { viewModel.setFilterNotRegex(it) },
                title = { Text(stringResource(Res.string.filter_regex)) },
                textToValue = { it },
                icon = { MaskedIcon(Res.drawable.delete_sweep, IconMaskColors.IconCoral) },
                summary = { Text(contentOrUnset(uiState.filterNotRegex)) },
                valueToText = { it },
            )
        }
    }

    if (uiState.collectType != ProxySetBean.TYPE_GROUP) {
        item("add_profile") {
            ElevatedCard(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            ) {
                Text(
                    text = stringResource(Res.string.add_profile),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                )
            }
        }

        item("list") {
            val density = LocalDensity.current
            val windowInfo = LocalWindowInfo.current
            val maxHeight =
                with(density) { windowInfo.containerSize.height.toDp() }.takeIf { it > 0.dp }
                    ?: 480.dp
            DragDropSwipeLazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
                items = uiState.profiles.toImmutableList(),
                key = { it.id },
                contentType = { 0 },
                userScrollEnabled = false,
                onIndicesChangedViaDragAndDrop = { viewModel.submitReorder(it) },
            ) { i, profile ->
                val swipeState = rememberSwipeToDismissBoxState()
                var visible by remember { mutableStateOf(true) }
                DraggableSwipeableItem(
                    modifier = Modifier.animateDraggableSwipeableItem(),
                    colors =
                        DraggableSwipeableItemColors.createRemembered(
                            containerBackgroundColor = Color.Transparent,
                            containerBackgroundColorWhileDragged = Color.Transparent,
                        ),
                ) {
                    AnimatedVisibility(
                        visible = visible,
                        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(),
                    ) {
                        SwipeToDismissBox(
                            state = swipeState,
                            enableDismissFromStartToEnd = true,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(vectorResource(Res.drawable.delete), null)
                                }
                            },
                            onDismiss = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.StartToEnd,
                                    SwipeToDismissBoxValue.EndToStart,
                                        -> visible = false

                                    else -> {}
                                }
                            },
                            modifier = Modifier.fillMaxWidth().dragDropModifier(),
                        ) {
                            ProxySetProfileCard(
                                profile = profile,
                                onReplace = { onReplace(i, profile.id) },
                                onRemove = { viewModel.remove(i) },
                            )
                        }
                    }
                }
                LaunchedEffect(visible) {
                    if (!visible) {
                        delay(220.milliseconds)
                        viewModel.remove(i)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProxySetProfileCard(profile: ProxyEntity, onReplace: () -> Unit, onRemove: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = profile.displayName(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TooltipIconButton(
                        onClick = onReplace,
                        icon = vectorResource(Res.drawable.edit),
                        contentDescription = stringResource(Res.string.edit),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    )
                    TooltipIconButton(
                        onClick = onRemove,
                        icon = vectorResource(Res.drawable.delete),
                        contentDescription = stringResource(Res.string.delete),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = profile.displayType(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
