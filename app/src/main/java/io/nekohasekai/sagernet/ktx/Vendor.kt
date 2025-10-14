/*************************************************************************
 * Copyright (C) 2022 by nekohasekai <contact-sagernet@sekai.icu>        *
 *                                                                       *
 * This program is free software: you can redistribute it and/or modify  *
 * it under the terms of the GNU General Public License as published by  *
 * the Free Software Foundation, either version 3 of the License, or     *
 * (at your option) any later version.                                   *
 *                                                                       *
 * This program is distributed in the hope that it will be useful,       *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 * GNU General Public License for more details.                          *
 *                                                                       *
 * You should have received a copy of the GNU General Public License     *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.  *
 *                                                                       *
 * In addition, no derivative work may use the name or imply association *
 * with this application without prior consent.                          *
 *************************************************************************/

package io.nekohasekai.sagernet.ktx

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Process

object MIUIUtils {

    private const val MIUI_VERSION_CODE = "ro.miui.ui.version.code"
    private const val MIUI_PERMISSION_EDITOR = "miui.intent.action.APP_PERM_EDITOR"
    private const val EXTRA_PACKAGE_UID = "extra_package_uid"
    private const val EXTRA_PACKAGE_NAME = "extra_pkgname"

    val isMIUI by lazy {
        !getSystemProperty(MIUI_VERSION_CODE).isNullOrBlank()
    }

    @SuppressLint("PrivateApi")
    fun getSystemProperty(key: String?): String? {
        return try {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (_: Exception) {
            null
        }
    }

    fun openPermissionSettings(context: Context) {
        val intent = Intent(MIUI_PERMISSION_EDITOR)
        intent.putExtra(EXTRA_PACKAGE_NAME, Process.myUid())
        intent.putExtra(EXTRA_PACKAGE_UID, context.packageName)
        context.startActivity(intent)
    }

}