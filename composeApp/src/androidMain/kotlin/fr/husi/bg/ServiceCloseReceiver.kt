package fr.husi.bg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import fr.husi.Action

class ServiceCloseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Action.CLOSE) return
        val service = ServiceRegistry.baseService
        if (service == null) {
            context.stopService(Intent(context, VpnService::class.java))
            context.stopService(Intent(context, ProxyService::class.java))
            NotificationManagerCompat.from(context).cancel(ServiceNotification.NOTIFICATION_ID)
        } else if (!service.data.closeReceiverRegistered) {
            service.stopRunner()
            context.stopService(Intent(context, VpnService::class.java))
            context.stopService(Intent(context, ProxyService::class.java))
            NotificationManagerCompat.from(context).cancel(ServiceNotification.NOTIFICATION_ID)
        }
    }
}
