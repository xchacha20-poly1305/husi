package fr.husi.bg

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy.UPDATE
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import fr.husi.repository.resolveAndroidRepository
import java.util.concurrent.TimeUnit

actual object RouteAssetUpdater {

    private const val WORK_NAME = "RouteAssetUpdater"

    actual suspend fun reconfigureUpdater() {
        val repository = resolveAndroidRepository()
        RemoteWorkManager.getInstance(repository.context).cancelUniqueWork(WORK_NAME)

        val plan = RouteAssetAutoUpdatePlanner.plan() ?: return
        val repeatIntervalMinutes = plan.repeatIntervalMinutes.coerceAtLeast(15).toLong()

        RemoteWorkManager.getInstance(repository.context).enqueueUniquePeriodicWork(
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
        appContext: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            RouteAssetAutoUpdateRunner.run()
            return Result.success()
        }
    }
}
