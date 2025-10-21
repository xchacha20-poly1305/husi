/******************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-git@sekai.icu>                  *
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

package io.nekohasekai.sagernet.tasker

import android.content.Intent
import android.os.Bundle

class TaskerBundle(val bundle: Bundle) {

    companion object {
        fun fromIntent(intent: Intent) =
            TaskerBundle(intent.getBundleExtra(EXTRA_BUNDLE) ?: Bundle())

        const val KEY_ACTION = "action"
        const val ACTION_START = 0
        const val ACTION_STOP = 1

        const val KEY_PROFILE_ID = "profile"

        const val EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
        const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
    }

    var action: Int
        get() = bundle.getInt(KEY_ACTION, ACTION_START)
        set(value) {
            bundle.putInt(KEY_ACTION, value)
        }

    var profileId: Long
        get() = bundle.getLong(KEY_PROFILE_ID, -1L)
        set(value) {
            bundle.putLong(KEY_PROFILE_ID, value)
        }

}