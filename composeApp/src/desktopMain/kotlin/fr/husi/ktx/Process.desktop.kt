package fr.husi.ktx

import kotlin.system.exitProcess

actual fun restartApplication() {
    exitProcess(0)
}

actual fun exitApplication() {
    exitProcess(1)
}
