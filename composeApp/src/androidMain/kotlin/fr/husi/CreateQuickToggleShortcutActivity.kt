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
import android.content.Intent
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import fr.husi.lib.R
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.quick_toggle
import kotlinx.coroutines.runBlocking

class CreateQuickToggleShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(
            RESULT_OK,
            ShortcutManagerCompat.createShortcutResultIntent(
                this,
                ShortcutInfoCompat.Builder(this, QuickToggleActivity.SHORTCUT_ID_TOGGLE)
                    .setIntent(
                        Intent(
                            this,
                            QuickToggleActivity::class.java,
                        ).setAction(Intent.ACTION_MAIN),
                    )
                    .setIcon(
                        IconCompat.createWithResource(
                            this,
                            R.drawable.ic_qu_shadowsocks_launcher,
                        ),
                    )
                    .setShortLabel(runBlocking { resolveRepository().getString(Res.string.quick_toggle) })
                    .build(),
            ),
        )
        finish()
    }
}
