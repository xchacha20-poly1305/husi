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

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.mieru.MieruBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import rikka.preference.SimpleMenuPreference

class MieruSettingsActivity : ProfileSettingsActivity<MieruBean>() {

    override fun createBean() = MieruBean().applyDefaultValues()

    override fun MieruBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverProtocol = protocol
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverMTU = mtu
        DataStore.serverMuxNumber = serverMuxNumber
    }

    override fun MieruBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        protocol = DataStore.serverProtocol
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        mtu = DataStore.serverMTU
        serverMuxNumber = DataStore.serverMuxNumber
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.mieru_preferences)
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        val protocol = findPreference<SimpleMenuPreference>(Key.SERVER_PROTOCOL)!!
        val mtu = findPreference<EditTextPreference>(Key.SERVER_MTU)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
        fun updateMtuVisibility(newValue: String = protocol.value) {
            mtu.isVisible = newValue == "UDP"
        }
        updateMtuVisibility()
        protocol.setOnPreferenceChangeListener { _, newValue ->
            updateMtuVisibility(newValue.toString())
            true
        }
    }

}