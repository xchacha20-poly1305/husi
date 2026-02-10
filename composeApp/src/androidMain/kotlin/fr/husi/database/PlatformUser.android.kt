package fr.husi.database

import android.os.Binder

actual fun callingUserIndex(): Int = Binder.getCallingUserHandle().hashCode()
