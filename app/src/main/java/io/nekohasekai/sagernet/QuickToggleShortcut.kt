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

package io.nekohasekai.sagernet

import android.app.Activity
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.repository.repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class QuickToggleShortcut : Activity() {

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
    }

    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_SHORTCUT)
    private var profileId = -1L
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            setResult(
                RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(
                    this,
                    ShortcutInfoCompat.Builder(this, "toggle")
                        .setIntent(
                            Intent(
                                this,
                                QuickToggleShortcut::class.java
                            ).setAction(Intent.ACTION_MAIN)
                        )
                        .setIcon(
                            IconCompat.createWithResource(
                                this,
                                R.drawable.ic_qu_shadowsocks_launcher
                            )
                        )
                        .setShortLabel(getString(R.string.quick_toggle))
                        .build()
                )
            )
            finish()
        } else {
            profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
            connection.connect(this)
            if (Build.VERSION.SDK_INT >= 25) {
                getSystemService<ShortcutManager>()!!.reportShortcutUsed(if (profileId >= 0) "shortcut-profile-$profileId" else "toggle")
            }
            job = scope.launch {
                val service = connection.service.filterNotNull().first()
                val state = BaseService.State.entries[service.state]
                when {
                    state.canStop -> {
                        if (profileId == DataStore.selectedProxy || profileId == -1L) {
                            repo.stopService()
                        } else {
                            DataStore.selectedProxy = profileId
                            repo.reloadService()
                        }
                    }

                    state == BaseService.State.Stopped -> {
                        if (profileId >= 0L) DataStore.selectedProxy = profileId
                        repo.startService()
                    }
                }
                finish()
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        connection.disconnect(this)
        super.onDestroy()
    }
}
