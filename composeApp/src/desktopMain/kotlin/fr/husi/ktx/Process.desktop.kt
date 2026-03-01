package fr.husi.ktx

import kotlin.system.exitProcess

const val RESTART_EXIT_CODE = 50

actual fun restartApplication() {
    // launcher will handle it
    exitProcess(RESTART_EXIT_CODE)
}

actual fun exitApplication() {
    exitProcess(0)
}
