package fr.husi.bg

import kotlinx.coroutines.CoroutineScope
import java.io.IOException

expect class GuardedProcessPool(onFatal: suspend (IOException) -> Unit) {
    fun start(
        cmd: List<String>,
        env: MutableMap<String, String> = mutableMapOf(),
        onRestartCallback: (suspend () -> Unit)? = null,
    )

    fun close(scope: CoroutineScope)
}
