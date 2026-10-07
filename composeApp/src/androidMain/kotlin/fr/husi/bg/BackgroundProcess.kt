package fr.husi.bg

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import fr.husi.Action
import fr.husi.repository.resolveAndroidRepository
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException

suspend fun <T> withBackgroundProcess(block: suspend () -> T): T {
    val repository = resolveAndroidRepository()
    if (repository.isBgProcess) return block()

    val context = repository.context
    val connected = CompletableDeferred<Unit>()
    val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            connected.complete(Unit)
        }

        override fun onServiceDisconnected(name: ComponentName?) = Unit

        override fun onBindingDied(name: ComponentName?) {
            connected.completeExceptionally(IOException("background process binding died"))
        }
    }
    val intent = Intent(context, SagerConnection.serviceClass).setAction(Action.SERVICE)
    try {
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            throw IOException("bind background process")
        }
        connected.await()
        return block()
    } finally {
        // Required even when bindService returned false.
        context.unbindService(connection)
    }
}
