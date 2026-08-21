package fr.husi.bg

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.husi.ktx.hasPermission
import fr.husi.lib.R
import fr.husi.repository.resolveRepository
import fr.husi.resources.Res
import fr.husi.resources.vpn_permission_required
import fr.husi.resources.vpn_permission_required_summary
import fr.husi.ui.VpnRequestActivity

/**
 * Fallback entry to the VPN consent dialog for the callers that cannot show it themselves.
 *
 * A service running in the background may not launch an activity since Android 10, so boot and
 * Tasker starts would otherwise fail without telling the user anything. A notification is the
 * sanctioned way to hand the launch back to them.
 */
object VpnRequestNotification {
    private const val NOTIFICATION_ID = 5
    private const val CHANNEL_ID = "service-vpn-request"

    suspend fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return
        }
        val repository = resolveRepository()
        val intent = Intent(context, VpnRequestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.vpn_key)
            .setContentTitle(repository.getString(Res.string.vpn_permission_required))
            .setContentText(repository.getString(Res.string.vpn_permission_required_summary))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

        @SuppressLint("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }
}
