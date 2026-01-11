package io.nekohasekai.sagernet.ui.profile

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ernestoyaquello.dragdropswipelazycolumn.DragDropSwipeLazyColumn
import com.ernestoyaquello.dragdropswipelazycolumn.DraggableSwipeableItem
import com.ernestoyaquello.dragdropswipelazycolumn.config.DraggableSwipeableItemColors
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.compose.DurationTextField
import io.nekohasekai.sagernet.compose.TooltipIconButton
import io.nekohasekai.sagernet.compose.UIntegerTextField
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.displayType
import io.nekohasekai.sagernet.fmt.internal.ProxySetBean
import io.nekohasekai.sagernet.ktx.contentOrUnset
import io.nekohasekai.sagernet.ktx.intListN
import io.nekohasekai.sagernet.ui.configuration.ProfileSelectActivity
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
class ProxySetSettingsActivity : ProfileSettingsActivity<ProxySetBean>() {

    override val viewModel by viewModels<ProxySetSettingsViewModel>()

    override val title = R.string.group_settings

    override fun LazyListScope.settings(
        uiState: ProfileSettingsUiState,
        scrollTo: (key: String) -> Unit,
    ) {
        uiState as ProxySetUiState

        item("name") {
            TextFieldPreference(
                value = uiState.name,
                onValueChange = { viewModel.setName(it) },
                title = { Text(stringResource(R.string.profile_name)) },
                textToValue = { it },
                icon = { Icon(ImageVector.vectorResource(R.drawable.emoji_symbols), null) },
                summary = { Text(LocalContext.current.contentOrUnset(uiState.name)) },
                valueToText = { it },
            )
        }
        item("management") {
            fun managementName(management: Int) = when (management) {
                ProxySetBean.MANAGEMENT_SELECTOR -> R.string.action_selector
                ProxySetBean.MANAGEMENT_URLTEST -> R.string.action_urltest
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.management,
                onValueChange = { viewModel.setManagement(it) },
                values = intListN(2),
                title = { Text(stringResource(R.string.management)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.widgets), null) },
                summary = { Text(stringResource(managementName(uiState.management))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(getString(managementName(it))) },
            )
        }
        item("interrupt_exist_connections") {
            SwitchPreference(
                value = uiState.interruptExistConnections,
                onValueChange = { viewModel.setInterruptExistConnections(it) },
                title = { Text(stringResource(R.string.interrupt_exist_connections)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.stop), null) },
            )
        }
        if (uiState.management == ProxySetBean.MANAGEMENT_URLTEST) {
            item("test_url") {
                TextFieldPreference(
                    value = uiState.testURL,
                    onValueChange = { viewModel.setTestURL(it) },
                    title = { Text(stringResource(R.string.connection_test_url)) },
                    textToValue = { it },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.cast_connected), null) },
                    summary = { Text(LocalContext.current.contentOrUnset(uiState.testURL)) },
                    valueToText = { it },
                )
            }
            item("test_interval") {
                TextFieldPreference(
                    value = uiState.testInterval,
                    onValueChange = { viewModel.setTestInterval(it) },
                    title = { Text(stringResource(R.string.urltest_interval)) },
                    textToValue = { it },
                    icon = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.flip_camera_android),
                            null,
                        )
                    },
                    summary = { Text(LocalContext.current.contentOrUnset(uiState.testInterval)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        DurationTextField(value, onValueChange, onOk)
                    },
                )
            }
            item("idle_timeout") {
                TextFieldPreference(
                    value = uiState.testIdleTimeout,
                    onValueChange = { viewModel.setTestIdleTimeout(it) },
                    title = { Text(stringResource(R.string.idle_timeout)) },
                    textToValue = { it },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.photo_camera), null) },
                    summary = { Text(LocalContext.current.contentOrUnset(uiState.testIdleTimeout)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        DurationTextField(value, onValueChange, onOk)
                    },
                )
            }
            item("tolerance") {
                TextFieldPreference(
                    value = uiState.testTolerance,
                    onValueChange = { viewModel.setTestTolerance(it) },
                    title = { Text(stringResource(R.string.urltest_tolerance)) },
                    textToValue = { it.toIntOrNull() ?: 50 },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.emoji_emotions), null) },
                    summary = { Text(uiState.testTolerance.toString()) },
                    valueToText = { it.toString() },
                    textField = { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    },
                )
            }
        }
        item("type") {
            fun typeName(type: Int) = when (type) {
                ProxySetBean.TYPE_LIST -> R.string.list
                ProxySetBean.TYPE_GROUP -> R.string.menu_group
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.collectType,
                onValueChange = { viewModel.setCollectType(it) },
                values = intListN(2),
                title = { Text(stringResource(R.string.group_type)) },
                icon = { Icon(ImageVector.vectorResource(R.drawable.nfc), null) },
                summary = { Text(stringResource(typeName(uiState.collectType))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(getString(typeName(it))) },
            )
        }
        if (uiState.collectType == ProxySetBean.TYPE_GROUP) {
            item("group") {
                ListPreference(
                    value = uiState.groupID,
                    onValueChange = { viewModel.setGroupID(it) },
                    values = uiState.groups.keys.toList(),
                    title = { Text(stringResource(R.string.menu_group)) },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.view_list), null) },
                    summary = {
                        val text = uiState.groups[uiState.groupID]?.displayName()
                            ?: stringResource(R.string.not_set)
                        Text(text)
                    },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { id ->
                        val text = uiState.groups[id]?.displayName() ?: getString(R.string.not_set)
                        AnnotatedString(text)
                    },
                )
            }
            item("filter_not_regex") {
                TextFieldPreference(
                    value = uiState.filterNotRegex,
                    onValueChange = { viewModel.setFilterNotRegex(it) },
                    title = { Text(stringResource(R.string.filter_regex)) },
                    textToValue = { it },
                    icon = { Icon(ImageVector.vectorResource(R.drawable.delete_sweep), null) },
                    summary = { Text(LocalContext.current.contentOrUnset(uiState.filterNotRegex)) },
                    valueToText = { it },
                )
            }
            return
        }

        item("divider", 1) {
            HorizontalDivider()
        }

        item("add_profile", 2) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .clickable {
                        viewModel.replacing = -1
                        selectProfileForAdd.launch(
                            Intent(
                                this@ProxySetSettingsActivity,
                                ProfileSelectActivity::class.java,
                            ),
                        )
                    },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Text(
                    text = stringResource(id = R.string.add_profile),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }

        item("list", 3) {
            val density = LocalDensity.current
            val windowInfo = LocalWindowInfo.current
            val maxHeight =
                with(density) { windowInfo.containerSize.height.toDp() }.takeIf { it > 0.dp }
                    ?: 480.dp
            DragDropSwipeLazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
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
                    colors = DraggableSwipeableItemColors.createRemembered(
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
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(ImageVector.vectorResource(R.drawable.delete), null)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .dragDropModifier(),
                        ) {
                            ProfileCard(i, profile)
                        }
                    }
                }
                LaunchedEffect(visible) {
                    if (!visible) {
                        delay(220)
                        viewModel.remove(i)
                    }
                }
            }
        }
    }

    @Composable
    private fun ProfileCard(index: Int, profile: ProxyEntity) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp),
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
                            onClick = {
                                viewModel.replacing = index
                                selectProfileForAdd.launch(
                                    Intent(
                                        this@ProxySetSettingsActivity,
                                        ProfileSelectActivity::class.java,
                                    ).putExtra(ProfileSelectActivity.EXTRA_SELECTED, profile.id),
                                )
                            },
                            icon = ImageVector.vectorResource(R.drawable.edit),
                            contentDescription = stringResource(R.string.edit),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        TooltipIconButton(
                            onClick = { viewModel.remove(index) },
                            icon = ImageVector.vectorResource(R.drawable.delete),
                            contentDescription = stringResource(R.string.delete),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = profile.displayType(LocalContext.current),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )

                }
            }
        }
    }

    val selectProfileForAdd =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                val id = it.data!!.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, 0)
                viewModel.onSelectProfile(id)
            }
        }

}