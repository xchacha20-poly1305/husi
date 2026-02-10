package fr.husi.bg

import fr.husi.ktx.Logs
import fr.husi.repository.repo
import fr.husi.utils.Commandline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.thread

actual class GuardedProcessPool actual constructor(
    private val onFatal: suspend (IOException) -> Unit,
) : CoroutineScope {
    private inner class Guard(
        private val cmd: List<String>,
        private val env: Map<String, String> = mapOf(),
    ) {
        private lateinit var process: Process

        private fun streamLogger(input: InputStream, logger: (String) -> Unit) = try {
            input.bufferedReader().forEachLine(logger)
        } catch (_: IOException) {
        }

        fun start() {
            process = ProcessBuilder(cmd).directory(repo.filesDir).apply {
                environment().putAll(env)
            }.start()
        }

        @DelicateCoroutinesApi
        suspend fun looper(onRestartCallback: (suspend () -> Unit)?) {
            var running = true
            val cmdName = File(cmd.first()).nameWithoutExtension
            val exitChannel = Channel<Int>()
            try {
                while (true) {
                    thread(name = "stderr-$cmdName") {
                        streamLogger(process.errorStream) { Logs.d("[$cmdName]$it") }
                    }
                    thread(name = "stdout-$cmdName") {
                        streamLogger(process.inputStream) { Logs.d("[$cmdName] $it") }
                        runBlocking { exitChannel.send(process.waitFor()) }
                    }
                    val startTime = System.currentTimeMillis()
                    val exitCode = exitChannel.receive()
                    running = false
                    when {
                        System.currentTimeMillis() - startTime < 1000 -> throw IOException(
                            "$cmdName exits too fast (exit code: $exitCode)",
                        )

                        else -> Logs.w(IOException("$cmdName unexpectedly exits with code $exitCode"))
                    }
                    Logs.i("restart process: ${Commandline.toString(cmd)} (last exit code: $exitCode)")
                    start()
                    running = true
                    onRestartCallback?.invoke()
                }
            } catch (e: IOException) {
                Logs.w("error occurred. stop guard: ${Commandline.toString(cmd)}")
                GlobalScope.launch(Dispatchers.Default) { onFatal(e) }
            } finally {
                if (running) withContext(NonCancellable) {
                    process.destroy()
                    if (withTimeoutOrNull(1000) { exitChannel.receive() } != null) return@withContext
                    process.destroyForcibly()
                    withTimeoutOrNull(1000) { exitChannel.receive() }
                }
            }
        }
    }

    override val coroutineContext = Dispatchers.Default + Job()

    @OptIn(DelicateCoroutinesApi::class)
    actual fun start(
        cmd: List<String>,
        env: MutableMap<String, String>,
        onRestartCallback: (suspend () -> Unit)?,
    ) {
        Logs.i("start process: ${Commandline.toString(cmd)}")
        Guard(cmd, env).apply {
            start()
            launch { looper(onRestartCallback) }
        }
    }

    actual fun close(scope: CoroutineScope) {
        cancel()
        coroutineContext[Job]!!.also { job -> scope.launch { job.cancelAndJoin() } }
    }
}
