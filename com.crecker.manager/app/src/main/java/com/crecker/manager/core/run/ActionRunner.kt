package com.crecker.manager.core.run

import android.content.Context
import com.crecker.manager.R
import com.crecker.manager.core.model.ActionContext
import com.crecker.manager.core.model.ActionLog
import com.crecker.manager.core.model.LogEntry
import com.crecker.manager.core.model.LogLevel
import com.crecker.manager.core.model.LogTable
import com.crecker.manager.core.model.RunRecord
import com.crecker.manager.core.model.RunStatus
import com.crecker.manager.core.model.RunTrigger
import com.crecker.manager.core.model.SystemAction
import com.crecker.manager.core.model.actionKey
import com.crecker.manager.core.model.describe
import com.crecker.manager.core.settings.localized
import com.crecker.manager.core.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * מריץ פעולות [SystemAction.Logged] – גם ידנית (מה-UI) וגם מתוזמנות (מ-WorkManager).
 *
 * - הרצות ידניות רצות ב-scope של האפליקציה, כך שהן ממשיכות גם אם יוצאים מהמסך.
 * - פעולה לא יכולה לרוץ פעמיים במקביל.
 * - הלוג מתפרסם חי דרך [runs], ונשמר להיסטוריה בסיום.
 * - התיקייה הזמנית נמחקת תמיד בסיום (גם בכשל או ביטול).
 */
class ActionRunner(
    private val appContext: Context,
    private val settingsStore: SettingsStore,
    private val history: RunHistory,
    private val scope: CoroutineScope,
) {
    private val _runs = MutableStateFlow<Map<String, RunRecord>>(emptyMap())

    /** ההרצה האחרונה (או הנוכחית) של כל פעולה מאז שהאפליקציה עלתה, לפי [actionKey]. */
    val runs: StateFlow<Map<String, RunRecord>> = _runs.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun start(systemId: String, action: SystemAction.Logged) {
        scope.launch { execute(systemId, action, RunTrigger.MANUAL) }
    }

    fun cancel(systemId: String, actionId: String) {
        synchronized(jobs) { jobs[actionKey(systemId, actionId)] }?.cancel()
    }

    /** מריץ ומחכה לסיום. מחזיר null אם הפעולה כבר רצה. */
    suspend fun execute(systemId: String, action: SystemAction.Logged, trigger: RunTrigger): RunRecord? {
        val key = actionKey(systemId, action.id)
        val job = currentCoroutineContext().job
        synchronized(jobs) {
            if (jobs.containsKey(key)) return null
            jobs[key] = job
        }

        val runId = UUID.randomUUID().toString().take(8)
        _runs.update {
            it + (key to RunRecord(runId, systemId, action.id, trigger, System.currentTimeMillis(), null, RunStatus.RUNNING, emptyList()))
        }
        val log = LiveLog(key)
        val workDir = File(appContext.cacheDir, "runs/$runId").apply { mkdirs() }
        val res = appContext.localized()
        val context = ActionContext(res, settingsStore.forAction(systemId, action.id), workDir, log, trigger)

        var status = RunStatus.FAILED
        try {
            log.info(res.getString(R.string.log_started, res.getString(trigger.label)))
            status = withContext(Dispatchers.IO) { action.run(context) }
        } catch (e: CancellationException) {
            status = RunStatus.CANCELLED
            log.warn(res.getString(R.string.log_cancelled))
            throw e
        } catch (e: Exception) {
            log.error(res.getString(R.string.log_unexpected_error, e.describe(res)))
        } finally {
            withContext(NonCancellable) {
                if (workDir.deleteRecursively()) log.info(res.getString(R.string.log_temp_deleted))
                else log.warn(res.getString(R.string.log_temp_not_deleted, workDir.path))
                log.add(if (status == RunStatus.SUCCESS) LogLevel.SUCCESS else LogLevel.INFO, res.getString(R.string.log_finished, res.getString(status.label)))
                _runs.update { it + (key to it.getValue(key).copy(status = status, endedAt = System.currentTimeMillis())) }
                history.save(_runs.value.getValue(key))
                synchronized(jobs) { jobs.remove(key) }
            }
        }
        return _runs.value.getValue(key)
    }

    private inner class LiveLog(private val key: String) : ActionLog {
        fun add(level: LogLevel, message: String, table: LogTable? = null) {
            val entry = LogEntry(System.currentTimeMillis(), level, message, table)
            _runs.update { it + (key to it.getValue(key).let { r -> r.copy(entries = r.entries + entry) }) }
        }

        override fun info(message: String) = add(LogLevel.INFO, message)
        override fun success(message: String) = add(LogLevel.SUCCESS, message)
        override fun warn(message: String) = add(LogLevel.WARN, message)
        override fun error(message: String) = add(LogLevel.ERROR, message)
        override fun table(title: String, headers: List<String>, rows: List<List<String>>) =
            add(LogLevel.INFO, title, LogTable(headers, rows))
    }
}

