/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.ui.profile

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

    override fun LazyListScope.settings(state: ProfileSettingsUiState) {
        state as ProxySetUiState

        item("name") {
            TextFieldPreference(
                value = state.name,
                onValueChange = { viewModel.setName(it) },
                title = { Text(stringResource(R.string.profile_name)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.EmojiSymbols, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.name)) },
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
                value = state.management,
                onValueChange = { viewModel.setManagement(it) },
                values = intListN(2),
                title = { Text(stringResource(R.string.management)) },
                icon = { Icon(Icons.Filled.Widgets, null) },
                summary = { Text(stringResource(managementName(state.management))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(getString(managementName(it))) },
            )
        }
        item("interrupt_exist_connections") {
            SwitchPreference(
                value = state.interruptExistConnections,
                onValueChange = { viewModel.setInterruptExistConnections(it) },
                title = { Text(stringResource(R.string.interrupt_exist_connections)) },
                icon = { Icon(Icons.Filled.Stop, null) },
            )
        }
        item("test_url") {
            TextFieldPreference(
                value = state.testURL,
                onValueChange = { viewModel.setTestURL(it) },
                title = { Text(stringResource(R.string.connection_test_url)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.CastConnected, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.testURL)) },
                valueToText = { it },
            )
        }
        item("test_interval") {
            TextFieldPreference(
                value = state.testInterval,
                onValueChange = { viewModel.setTestInterval(it) },
                title = { Text(stringResource(R.string.urltest_interval)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.FlipCameraAndroid, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.testInterval)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    DurationTextField(value, onValueChange, onOk)
                },
            )
        }
        item("idle_timeout") {
            TextFieldPreference(
                value = state.testIdleTimeout,
                onValueChange = { viewModel.setTestIdleTimeout(it) },
                title = { Text(stringResource(R.string.idle_timeout)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.PhotoCamera, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.testIdleTimeout)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    DurationTextField(value, onValueChange, onOk)
                },
            )
        }
        item("tolerance") {
            TextFieldPreference(
                value = state.testTolerance,
                onValueChange = { viewModel.setTestTolerance(it) },
                title = { Text(stringResource(R.string.urltest_tolerance)) },
                textToValue = { it.toIntOrNull() ?: 50 },
                icon = { Icon(Icons.Filled.EmojiEmotions, null) },
                summary = { Text(state.testTolerance.toString()) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
        }
        item("type") {
            fun typeName(type: Int) = when (type) {
                ProxySetBean.TYPE_LIST -> R.string.list
                ProxySetBean.TYPE_GROUP -> R.string.menu_group
                else -> error("impossible")
            }
            ListPreference(
                value = state.collectType,
                onValueChange = { viewModel.setCollectType(it) },
                values = intListN(2),
                title = { Text(stringResource(R.string.group_type)) },
                icon = { Icon(Icons.Filled.Nfc, null) },
                summary = { Text(stringResource(typeName(state.collectType))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(getString(typeName(it))) },
            )
        }
        item("group") {
            ListPreference(
                value = state.groupID,
                onValueChange = { viewModel.setGroupID(it) },
                values = state.groups.keys.toList(),
                title = { Text(stringResource(R.string.menu_group)) },
                icon = { Icon(Icons.AutoMirrored.Filled.ViewList, null) },
                summary = {
                    val text = state.groups[state.groupID]?.displayName()
                        ?: stringResource(androidx.preference.R.string.not_set)
                    Text(text)
                },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { id ->
                    val text =
                        state.groups[id]?.displayName()
                            ?: getString(androidx.preference.R.string.not_set)
                    AnnotatedString(text)
                },
            )
        }
        item("filter_not_regex") {
            TextFieldPreference(
                value = state.filterNotRegex,
                onValueChange = { viewModel.setFilterNotRegex(it) },
                title = { Text(stringResource(R.string.filter_regex)) },
                textToValue = { it },
                icon = { Icon(Icons.Filled.DeleteSweep, null) },
                summary = { Text(LocalContext.current.contentOrUnset(state.filterNotRegex)) },
                valueToText = { it },
            )
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
                            )
                        )
                    },
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
            ) {
                Text(
                    text = stringResource(id = R.string.add_profile),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold
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
                items = state.profiles.toImmutableList(),
                key = { it.id },
                contentType = { 0 },
                userScrollEnabled = false,
                onIndicesChangedViaDragAndDrop = { viewModel.submitList(it.map { it.value }) },
            ) { i, profile ->
                val swipeState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value: SwipeToDismissBoxValue ->
                        when (value) {
                            SwipeToDismissBoxValue.StartToEnd,
                            SwipeToDismissBoxValue.EndToStart -> {
                                visible.value = false
                            }
                            else -> {}
                        }
                    },
                )
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
                            backgroundContent = { state: SwipeToDismissBoxState ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(Icons.Default.Delete, null)
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
                        IconButton(onClick = {
                            viewModel.replacing = index
                            selectProfileForAdd.launch(
                                Intent(
                                    this@ProxySetSettingsActivity,
                                    ProfileSelectActivity::class.java,
                                ).putExtra(ProfileSelectActivity.EXTRA_SELECTED, profile),
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.edit),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { viewModel.remove(index) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
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
