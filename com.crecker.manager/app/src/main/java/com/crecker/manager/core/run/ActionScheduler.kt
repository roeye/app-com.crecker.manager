package com.crecker.manager.core.run

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.crecker.manager.app.SystemRegistry
import com.crecker.manager.core.model.RunStatus
import com.crecker.manager.core.model.RunTrigger
import com.crecker.manager.core.model.SystemAction
import com.crecker.manager.core.settings.ActionSettings
import com.crecker.manager.graph
import java.util.concurrent.TimeUnit

/** מפתחות ההגדרות של תזמון – משותפים לכל פעולה שהיא `schedulable`. */
object ScheduleKeys {
    const val ENABLED = "schedule_enabled"
    const val INTERVAL_MINUTES = "schedule_interval_minutes"
    const val NOTIFY_ON_FAILURE = "schedule_notify_on_failure"

    /** המינימום של WorkManager להרצה מחזורית. */
    const val MIN_INTERVAL_MINUTES = 15
    const val DEFAULT_INTERVAL_MINUTES = 60
}

object ActionScheduler {
    private fun workName(systemId: String, actionId: String) = "scheduled:$systemId/$actionId"

    fun apply(context: Context, settings: ActionSettings, enabled: Boolean, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        val name = workName(settings.systemId, settings.actionId)
        if (!enabled) {
            workManager.cancelUniqueWork(name)
            return
        }
        val request = PeriodicWorkRequestBuilder<ScheduledActionWorker>(
            intervalMinutes.coerceAtLeast(ScheduleKeys.MIN_INTERVAL_MINUTES).toLong(), TimeUnit.MINUTES,
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(KEY_SYSTEM to settings.systemId, KEY_ACTION to settings.actionId))
            .build()
        workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    internal const val KEY_SYSTEM = "systemId"
    internal const val KEY_ACTION = "actionId"
}

class ScheduledActionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val system = SystemRegistry.find(inputData.getString(ActionScheduler.KEY_SYSTEM))
        val action = system?.action(inputData.getString(ActionScheduler.KEY_ACTION).orEmpty()) as? SystemAction.Logged
            ?: return Result.failure()

        val graph = applicationContext.graph
        // null = הפעולה כבר רצה (למשל ידנית) – מדלגים על הסבב הזה.
        val record = graph.runner.execute(system.id, action, RunTrigger.SCHEDULED) ?: return Result.success()

        val settings = graph.settings.forAction(system.id, action.id)
        if (record.status != RunStatus.SUCCESS && settings.getBoolean(ScheduleKeys.NOTIFY_ON_FAILURE, true)) {
            Notifications.runProblem(applicationContext, system.name, action.title, record)
        }
        return Result.success()
    }
}
