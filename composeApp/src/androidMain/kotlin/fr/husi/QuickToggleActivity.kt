/*******************************************************************************
 *                                                                             *
 *  Copyright (C) 2017 by Max Lv <max.c.lv@gmail.com>                          *
 *  Copyright (C) 2017 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
 *                                                                             *
 *  This program is free software: you can redistribute it and/or modify       *
 *  it under the terms of the GNU General Public License as published by       *
 *  the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                        *
 *                                                                             *
 *  This program is distributed in the hope that it will be useful,            *
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 *  GNU General Public License for more details.                               *
 *                                                                             *
 *  You should have received a copy of the GNU General Public License          *
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                             *
 *******************************************************************************/

package fr.husi

import android.app.Activity
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.getSystemService
import fr.husi.bg.ServiceState
import fr.husi.database.DataStore
import fr.husi.repository.resolveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class QuickToggleActivity : Activity() {

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"

        const val SHORTCUT_ID_TOGGLE = "toggle"
        const val SHORTCUT_ID_PROFILE_PREFIX = "shortcut-profile-"

        private const val NO_PROFILE_ID = -1L

        fun shortcutId(profileId: Long): String = if (profileId >= 0) {
            SHORTCUT_ID_PROFILE_PREFIX + profileId
        } else {
            SHORTCUT_ID_TOGGLE
        }
    }

    private var profileId = NO_PROFILE_ID
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileId = intent.getLongExtra(EXTRA_PROFILE_ID, NO_PROFILE_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            getSystemService<ShortcutManager>()!!.reportShortcutUsed(shortcutId(profileId))
        }
        job = scope.launch {
            toggle()
            finish()
        }
    }

    private suspend fun toggle() {
        val state = DataStore.serviceState
        when {
            state.canStop -> {
                if (profileId == DataStore.selectedProxy.get() || profileId == NO_PROFILE_ID) {
                    resolveRepository().stopService()
                } else {
                    DataStore.selectedProxy.set(profileId)
                    resolveRepository().reloadService()
                }
            }

            state == ServiceState.Stopped || state == ServiceState.Idle -> {
                if (profileId >= 0L) DataStore.selectedProxy.set(profileId)
                resolveRepository().startService()
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }
}
