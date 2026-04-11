package fr.husi.bg

import fr.husi.ktx.Logs
import fr.husi.ktx.runOnDefaultDispatcher

internal data class DesktopTaskSchedule(
    val repeatIntervalMinutes: Int,
    val initialDelaySeconds: Long,
)

internal interface DesktopTaskDefinition {
    val id: String
    val launcherArguments: List<String>

    suspend fun schedule(): DesktopTaskSchedule?
    suspend fun run()
}

internal object DesktopTaskRegistry {
    private val subscriptionAutoUpdateTask = object : DesktopTaskDefinition {
        override val id: String = "subscription-auto-update"
        override val launcherArguments: List<String> = listOf("--task", id)

        override suspend fun schedule(): DesktopTaskSchedule? {
            val plan = SubscriptionAutoUpdatePlanner.plan() ?: return null
            return DesktopTaskSchedule(
                repeatIntervalMinutes = plan.repeatIntervalMinutes,
                initialDelaySeconds = plan.initialDelaySeconds,
            )
        }

        override suspend fun run() {
            try {
                SubscriptionAutoUpdateRunner.run()
            } finally {
                SubscriptionUpdater.reconfigureUpdater()
            }
        }
    }

    private val definitions = listOf(subscriptionAutoUpdateTask)
        .associateBy(DesktopTaskDefinition::id)

    fun require(taskId: String): DesktopTaskDefinition {
        return definitions[taskId] ?: error("Unknown desktop task: $taskId")
    }

    fun dispatch(taskId: String) {
        runOnDefaultDispatcher {
            runCatching {
                require(taskId).run()
            }.onFailure {
                Logs.e("run desktop task $taskId", it)
            }
        }
    }
}
