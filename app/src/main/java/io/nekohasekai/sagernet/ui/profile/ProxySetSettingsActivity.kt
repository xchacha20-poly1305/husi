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
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.databinding.LayoutAddEntityBinding
import io.nekohasekai.sagernet.databinding.LayoutProfileBinding
import io.nekohasekai.sagernet.fmt.internal.ProxySetBean
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.mapX
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.configuration.ProfileSelectActivity
import io.nekohasekai.sagernet.widget.DurationPreference
import io.nekohasekai.sagernet.widget.setGroupBean
import rikka.preference.SimpleMenuPreference

class ProxySetSettingsActivity :
    ProfileSettingsActivity<ProxySetBean>(R.layout.layout_chain_settings) {

    override fun createBean() = ProxySetBean().applyDefaultValues()

    private val proxyList = mutableListOf<ProxyEntity>()

    override fun ProxySetBean.init() {
        DataStore.profileName = name
        DataStore.serverManagement = management
        DataStore.serverInterruptExistConnections = interruptExistConnections
        DataStore.serverTestURL = testURL
        DataStore.serverTestInterval = testInterval
        DataStore.serverIdleTimeout = testIdleTimeout
        DataStore.serverTolerance = testTolerance
        DataStore.serverType = type
        DataStore.serverGroup = groupId
        DataStore.serverProxies = proxies.joinToString(",")
    }

    override fun ProxySetBean.serialize() {
        name = DataStore.profileName
        management = DataStore.serverManagement
        interruptExistConnections = DataStore.serverInterruptExistConnections
        testURL = DataStore.serverTestURL
        testInterval = DataStore.serverTestInterval
        testIdleTimeout = DataStore.serverIdleTimeout
        testTolerance = DataStore.serverTolerance
        type = DataStore.serverType
        groupId = DataStore.serverGroup
        proxies = proxyList.mapX { it.id }
    }

    private lateinit var groupType: SimpleMenuPreference
    private lateinit var groupPreference: SimpleMenuPreference
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
        serverManagement = findPreference(Key.SERVER_MANAGEMENT)!!
        testURL = findPreference(Key.SERVER_TEST_URL)!!
        testInterval = findPreference(Key.SERVER_TEST_INTERVAL)!!
        idleTimeout = findPreference(Key.SERVER_IDLE_TIMEOUT)!!
        tolerance = findPreference<EditTextPreference>(Key.SERVER_TOLERANCE)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }

        itemView = findViewById(R.id.list_cell)

        fun updateType(type: Int = groupType.value.toInt()) {
            when (type) {
                ProxySetBean.TYPE_LIST -> {
                    groupPreference.isVisible = false
                    configurationList.isVisible = true
                    itemView.isVisible = true
                }

                ProxySetBean.TYPE_GROUP -> {
                    groupPreference.isVisible = true
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

    lateinit var itemView: LinearLayout
    lateinit var configurationList: RecyclerView
    lateinit var configurationAdapter: ProxiesAdapter
    lateinit var layoutManager: LinearLayoutManager

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar!!.setTitle(R.string.group_settings)
        configurationList = findViewById(R.id.configuration_list)
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
        layoutManager = FixedLinearLayoutManager(configurationList)
        configurationList.layoutManager = layoutManager
        configurationAdapter = ProxiesAdapter()
        configurationList.adapter = configurationAdapter

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
                configurationAdapter.move(
                    viewHolder.absoluteAdapterPosition, target.absoluteAdapterPosition,
                )
                true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                configurationAdapter.remove(viewHolder.absoluteAdapterPosition)
            }

        }).attachToRecyclerView(configurationList)

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

        runOnDefaultDispatcher {
            configurationAdapter.reload()
        }
    }

    inner class ProxiesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        suspend fun reload() {
            val idList = DataStore.serverProxies.split(",")
                .mapNotNull { it.takeIf { it.isNotBlank() }?.toLong() }
            if (idList.isNotEmpty()) {
                val profiles = ProfileManager.getProfiles(idList).associate { it.id to it }
                for (id in idList) {
                    proxyList.add(profiles[id] ?: continue)
                }
            }
            onMainDispatcher {
                notifyDataSetChanged()
            }
        }

        fun move(from: Int, to: Int) {
            val toMove = proxyList[to - 1]
            proxyList[to - 1] = proxyList[from - 1]
            proxyList[from - 1] = toMove
            notifyItemMoved(from, to)
            DataStore.dirty = true
            onBackPressedCallback.isEnabled = true
        }

        fun remove(index: Int) {
            proxyList.removeAt(index - 1)
            notifyItemRemoved(index)
            DataStore.dirty = true
            onBackPressedCallback.isEnabled = true
        }

        override fun getItemId(position: Int): Long {
            return if (position == 0) 0 else proxyList[position - 1].id
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) 0 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                AddHolder(LayoutAddEntityBinding.inflate(layoutInflater, parent, false))
            } else {
                ProfileHolder(LayoutProfileBinding.inflate(layoutInflater, parent, false))
            }
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

    fun testProfileAllowed(profile: ProxyEntity): Boolean {
        if (profile.id == DataStore.editingId) return false

        for (entity in proxyList) {
            if (testProfileContains(entity, profile)) return false
        }

        return true
    }

    fun testProfileContains(profile: ProxyEntity, anotherProfile: ProxyEntity): Boolean {
        if (profile.type != ProxyEntity.TYPE_CHAIN || anotherProfile.type != ProxyEntity.TYPE_CHAIN) {
            return false
        }
        if (profile.id == anotherProfile.id) return true
        val proxies = profile.chainBean!!.proxies
        if (proxies.contains(anotherProfile.id)) return true
        if (proxies.isNotEmpty()) {
            for (entity in ProfileManager.getProfiles(proxies)) {
                if (testProfileContains(entity, anotherProfile)) {
                    return true
                }
            }
        }
        return false
    }

    var replacing = 0

    val selectProfileForAdd =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { (resultCode, data) ->
            if (resultCode == RESULT_OK) runOnDefaultDispatcher {
                DataStore.dirty = true
                onBackPressedCallback.isEnabled = true

                val profile = ProfileManager.getProfile(
                    data!!.getLongExtra(
                        ProfileSelectActivity.EXTRA_PROFILE_ID, 0
                    )
                )!!

                if (!testProfileAllowed(profile)) {
                    onMainDispatcher {
                        MaterialAlertDialogBuilder(this@ProxySetSettingsActivity)
                            .setTitle(R.string.circular_reference)
                            .setMessage(R.string.circular_reference_sum)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                } else {
                    configurationList.post {
                        if (replacing != 0) {
                            proxyList[replacing - 1] = profile
                            configurationAdapter.notifyItemChanged(replacing)
                        } else {
                            proxyList.add(profile)
                            configurationAdapter.notifyItemInserted(proxyList.size)
                        }
                    }
                }
            }
        }

    inner class AddHolder(val binding: LayoutAddEntityBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.setOnClickListener {
                replacing = 0
                selectProfileForAdd.launch(
                    Intent(
                        this@ProxySetSettingsActivity, ProfileSelectActivity::class.java
                    )
                )
            }
        }
    }

    inner class ProfileHolder(binding: LayoutProfileBinding) :
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
                replacing = absoluteAdapterPosition
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
                        configurationAdapter.remove(absoluteAdapterPosition)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            shareLayout.isVisible = false

        }

    }

}