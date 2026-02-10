package fr.husi.plugin

import android.content.pm.ComponentInfo
import fr.husi.repository.androidRepo

fun ComponentInfo.loadString(key: String) =
    when (@Suppress("DEPRECATION") val value = metaData.get(key)) {
        is String -> value
        is Int -> androidRepo.packageManager
            .getResourcesForApplication(applicationInfo)
            .getString(value)

        null -> null
        else -> error("meta-data $key has invalid type ${value.javaClass}")
    }
