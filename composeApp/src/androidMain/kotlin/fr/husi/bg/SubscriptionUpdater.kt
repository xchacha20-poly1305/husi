package fr.husi.bg

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy.UPDATE
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import fr.husi.lib.R
import fr.husi.repository.resolveAndroidRepository
import fr.husi.repository.resolveRepository
import fr.husi.resources.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

actual object SubscriptionUpdater {

    private const val WORK_NAME = "SubscriptionUpdater"

    actual suspend fun reconfigureUpdater() {
        val repo = resolveAndroidRepository()
        RemoteWorkManager.getInstance(repo.context).cancelUniqueWork(WORK_NAME)

        val plan = SubscriptionAutoUpdatePlanner.plan() ?: return
        val repeatIntervalMinutes = plan.repeatIntervalMinutes.coerceAtLeast(15).toLong()

        // main process
        RemoteWorkManager.getInstance(repo.context).enqueueUniquePeriodicWork(
            WORK_NAME,
            UPDATE,
            PeriodicWorkRequest.Builder(UpdateTask::class.java, repeatIntervalMinutes, TimeUnit.MINUTES)
                .apply {
                    if (plan.initialDelaySeconds > 0) {
                        setInitialDelay(plan.initialDelaySeconds, TimeUnit.SECONDS)
                    }
                }
                .build(),
        )
    }

    class UpdateTask(
        appContext: Context, params: WorkerParameters,
    ) : CoroutineWorker(appContext, params) {

        val nm = NotificationManagerCompat.from(applicationContext)

        val notification = runBlocking {
            val repo = resolveAndroidRepository()
            NotificationCompat.Builder(applicationContext, "service-subscription")
                .setWhen(0)
                .setTicker(repo.getString(Res.string.forward_success))
                .setContentTitle(repo.getString(Res.string.subscription_update))
                .setSmallIcon(R.drawable.ic_service_active)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
        }

        override suspend fun doWork(): Result {
            SubscriptionAutoUpdateRunner.run { profile ->
                notification.setContentText(
                    resolveRepository().getString(
                        Res.string.subscription_update_message,
                        profile.displayName(),
                    ),
                )
                nm.notify(2, notification.build())
            }

            nm.cancel(2)

            return Result.success()
        }
    }

}
