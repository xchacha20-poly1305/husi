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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.databinding.LayoutAddEntityBinding
import io.nekohasekai.sagernet.databinding.LayoutProfileBinding
import io.nekohasekai.sagernet.fmt.internal.ProxySetBean
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.configuration.ProfileSelectActivity
import io.nekohasekai.sagernet.widget.DurationPreference
import io.nekohasekai.sagernet.widget.setGroupBean
import kotlinx.coroutines.launch
import rikka.preference.SimpleMenuPreference

@OptIn(ExperimentalMaterial3Api::class)
class ProxySetSettingsActivity :
    ProfileSettingsActivity<ProxySetBean>(R.layout.layout_chain_settings) {

    private lateinit var groupType: SimpleMenuPreference
    private lateinit var groupPreference: SimpleMenuPreference
    private lateinit var groupFilterNotPreference: EditTextPreference
    private lateinit var serverManagement: SimpleMenuPreference
    private lateinit var testURL: EditTextPreference
    private lateinit var testInterval: DurationPreference
    private lateinit var idleTimeout: DurationPreference
    private lateinit var tolerance: EditTextPreference

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.proxy_set_preferences)

        groupType = findPreference(Key.SERVER_TYPE)!!
        groupPreference = findPreference<SimpleMenuPreference>(Key.SERVER_GROUP)!!.apply {
            setGroupBean()
        }
        groupFilterNotPreference = findPreference(Key.SERVER_FILTER_NOT_REGEX)!!
        serverManagement = findPreference(Key.SERVER_MANAGEMENT)!!
        testURL = findPreference(Key.SERVER_TEST_URL)!!
        testInterval = findPreference(Key.SERVER_TEST_INTERVAL)!!
        idleTimeout = findPreference(Key.SERVER_IDLE_TIMEOUT)!!
        tolerance = findPreference<EditTextPreference>(Key.SERVER_TOLERANCE)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }

        fun updateType(type: Int = groupType.value.toInt()) {
            when (type) {
                ProxySetBean.TYPE_LIST -> {
                    groupPreference.isVisible = false
                    groupFilterNotPreference.isVisible = false
                    configurationList.isVisible = true
                    itemView.isVisible = true
                }

                ProxySetBean.TYPE_GROUP -> {
                    groupPreference.isVisible = true
                    groupFilterNotPreference.isVisible = true
                    configurationList.isVisible = false
                    itemView.isVisible = false
                }
            }
        }
        updateType()
        groupType.setOnPreferenceChangeListener { _, newValue ->
            updateType(newValue.toString().toInt())
            true
        }

        fun updateManagement(management: Int = serverManagement.value.toInt()) {
            when (management) {
                ProxySetBean.MANAGEMENT_SELECTOR -> {
                    testURL.isVisible = false
                    testInterval.isVisible = false
                    idleTimeout.isVisible = false
                    tolerance.isVisible = false
                }

                ProxySetBean.MANAGEMENT_URLTEST -> {
                    testURL.isVisible = true
                    testInterval.isVisible = true
                    idleTimeout.isVisible = true
                    tolerance.isVisible = true
                }
            }
        }
        updateManagement()
        findPreference<SimpleMenuPreference>(Key.SERVER_MANAGEMENT)!!.apply {
            setOnPreferenceChangeListener { _, newValue ->
                updateManagement(newValue.toString().toInt())
                true
            }
        }
    }

    override val viewModel by viewModels<ProxySetSettingsViewModel>()
    private val itemView: LinearLayout by lazy { findViewById(R.id.list_cell) }
    private val configurationList: RecyclerView by lazy { findViewById(R.id.configuration_list) }
    private lateinit var configurationAdapter: ProxiesAdapter

    override val title = R.string.group_settings

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(configurationList) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left + dp2px(4),
                right = bars.right + dp2px(4),
                bottom = bars.bottom + dp2px(4),
            )
            insets
        }
        configurationList.adapter = ProxiesAdapter().also {
            configurationAdapter = it
        }

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.START,
        ) {
            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int = if (viewHolder is ProfileHolder) {
                super.getSwipeDirs(recyclerView, viewHolder)
            } else {
                0
            }

            override fun getDragDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ) = if (viewHolder is ProfileHolder) {
                super.getDragDirs(recyclerView, viewHolder)
            } else {
                0
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean = if (target !is ProfileHolder) {
                false
            } else {
                lifecycleScope.launch {
                    val from = viewHolder.absoluteAdapterPosition - 1
                    val to = target.absoluteAdapterPosition - 1
                    viewModel.move(from, to)
                }
                true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                lifecycleScope.launch {
                    val index = viewHolder.absoluteAdapterPosition - 1
                    viewModel.remove(index)
                }
            }

        }).attachToRecyclerView(configurationList)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::handleUiState)
            }
        }
    }

    private fun handleUiState(state: ProxySetSettingsUiState) {
        configurationAdapter.submitList(state.profiles)
    }

    override fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
        // override the padding in ProfileSettingsActivity
        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
            )
            insets
        }

        view.rootView.findViewById<RecyclerView>(R.id.recycler_view).apply {
            (layoutParams ?: LinearLayout.LayoutParams(-1, -2)).apply {
                height = -2
                layoutParams = this
            }
        }

    }

    private inner class ProxiesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        init {
            setHasStableIds(true)
        }

        private val proxyList = mutableListOf<ProxyEntity>()

        fun submitList(list: List<ProxyEntity>) {
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = proxyList.size
                override fun getNewListSize() = list.size
                override fun areItemsTheSame(o: Int, n: Int) =
                    ProxyEntityDiffCallback.areItemsTheSame(proxyList[o], list[n])
                override fun areContentsTheSame(o: Int, n: Int) =
                    ProxyEntityDiffCallback.areContentsTheSame(proxyList[o], list[n])
            })
            proxyList.clear()
            proxyList.addAll(list)
            val headerOffset = 1
            diff.dispatchUpdatesTo(object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) {
                    notifyItemRangeInserted(position + headerOffset, count)
                }
                override fun onRemoved(position: Int, count: Int) {
                    notifyItemRangeRemoved(position + headerOffset, count)
                }
                override fun onMoved(fromPosition: Int, toPosition: Int) {
                    notifyItemMoved(fromPosition + headerOffset, toPosition + headerOffset)
                }
                override fun onChanged(position: Int, count: Int, payload: Any?) {
                    notifyItemRangeChanged(position + headerOffset, count, payload)
                }
            })
        }


        override fun getItemId(position: Int): Long {
            return if (position == 0) 0 else proxyList[position - 1].id
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) 0 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) AddHolder(
                LayoutAddEntityBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            ) else ProfileHolder(
                LayoutProfileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
            )
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is AddHolder) {
                holder.bind()
            } else if (holder is ProfileHolder) {
                holder.bind(proxyList[position - 1])
            }
        }

        override fun getItemCount(): Int {
            return proxyList.size + 1
        }

    }

    private object ProxyEntityDiffCallback : DiffUtil.ItemCallback<ProxyEntity>() {
        override fun areItemsTheSame(old: ProxyEntity, new: ProxyEntity): Boolean {
            return old.id == new.id
        }

        override fun areContentsTheSame(old: ProxyEntity, new: ProxyEntity): Boolean {
            return true
        }

    }

    val selectProfileForAdd =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { (resultCode, data) ->
            if (resultCode == RESULT_OK) runOnDefaultDispatcher {
                val id = data!!.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, 0)
                viewModel.onSelectProfile(id)
            }
        }

    private inner class AddHolder(val binding: LayoutAddEntityBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.setOnClickListener {
                viewModel.replacing = 0
                selectProfileForAdd.launch(
                    Intent(
                        this@ProxySetSettingsActivity, ProfileSelectActivity::class.java
                    )
                )
            }
        }
    }

    private inner class ProfileHolder(binding: LayoutProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        val profileName = binding.profileName
        val profileType = binding.profileType
        val trafficText: TextView = binding.trafficText
        val editButton = binding.edit
        val deleteButton = binding.remove
        val shareLayout = binding.share

        fun bind(proxyEntity: ProxyEntity) {

            profileName.text = proxyEntity.displayName()
            profileType.text = proxyEntity.displayType()

            val rx = proxyEntity.rx
            val tx = proxyEntity.tx

            val showTraffic = rx + tx != 0L
            trafficText.isVisible = showTraffic
            if (showTraffic) {
                trafficText.text = itemView.context.getString(
                    R.string.traffic,
                    Formatter.formatFileSize(itemView.context, tx),
                    Formatter.formatFileSize(itemView.context, rx)
                )
            }

            editButton.setOnClickListener {
                viewModel.replacing = absoluteAdapterPosition
                selectProfileForAdd.launch(
                    Intent(
                        this@ProxySetSettingsActivity, ProfileSelectActivity::class.java
                    ).apply {
                        putExtra(ProfileSelectActivity.EXTRA_SELECTED, proxyEntity)
                    })
            }

            deleteButton.setOnClickListener {
                MaterialAlertDialogBuilder(this@ProxySetSettingsActivity)
                    .setTitle(getString(R.string.delete_confirm_prompt))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch {
                            val index = absoluteAdapterPosition - 1
                            viewModel.remove(index)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            shareLayout.isVisible = false

        }

    }

}